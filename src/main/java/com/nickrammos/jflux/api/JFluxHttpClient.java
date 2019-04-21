package com.nickrammos.jflux.api;

import java.io.IOException;
import java.util.regex.Pattern;

import com.nickrammos.jflux.api.response.ApiResponse;
import com.nickrammos.jflux.api.response.QueryResult;
import com.nickrammos.jflux.domain.Series;

import okhttp3.ResponseBody;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import retrofit2.Call;
import retrofit2.Retrofit;

/**
 * Makes calls to the InfluxDB HTTP API.
 *
 * @see Builder
 */
public final class JFluxHttpClient implements AutoCloseable {

	private static final Logger LOGGER = LoggerFactory.getLogger(JFluxHttpClient.class);

	private static final Pattern MULTI_SERIES_PATTERN = Pattern.compile("SELECT .* FROM .+,.+");
	private static final Pattern SELECT_INTO_PATTERN = Pattern.compile("SELECT .* INTO .* FROM "
			+ ".*");

	private final InfluxHttpService service;

	/**
	 * Initializes a new instance setting the service to be used for calls to the API.
	 *
	 * @param service the API service
	 */
	private JFluxHttpClient(InfluxHttpService service) {
		this.service = service;
	}

	/**
	 * Tests the connection to the InfluxDB API and returns the result.
	 *
	 * @return {@code true} if the API is reachable, {@code false} otherwise
	 */
	public boolean isConnected() {
		Call<ResponseBody> call = service.ping();
		try {
			retrofit2.Response response = call.execute();
			return response.isSuccessful();
		} catch (IOException e) {
			return false;
		}
	}

	/**
	 * Executes a query and returns the result.
	 * <p>
	 * This method expects that the query will produce a single result, i.e. is a single statement,
	 * querying a single measurement. The following example query can be executed with this method:
	 * <p><blockquote><pre>{@code
	 * SELECT * FROM measurement_1
	 * }</pre></blockquote><p>
	 * The following queries will result in an exception:
	 * <p><blockquote><pre>{@code
	 * SELECT * FROM measurement_1, measurement_2
	 * SELECT * FROM measurement_1; SELECT * FROM measurement_2
	 * }</pre></blockquote><p>
	 * For multi-series or batch queries, see {@link #queryMultipleSeries(String)} and
	 * {@link #batchQuery(String)} respectively instead.
	 *
	 * @param query the query to execute
	 *
	 * @return the query result
	 *
	 * @throws IOException if query execution fails
	 * @see #queryMultipleSeries(String)
	 * @see #batchQuery(String)
	 */
	public Series query(String query) throws IOException {
		if (MULTI_SERIES_PATTERN.matcher(query).matches()) {
			throw new IllegalArgumentException("Query cannot span multiple measurements");
		}

		return queryMultipleSeries(query).getSeries().get(0);
	}

	/**
	 * Executes a query and returns the result.
	 * <p>
	 * This method can be used to query across multiple series. Queries such as the following can
	 * be used:
	 * <p><blockquote><pre>{@code
	 * SELECT * FROM measurement_1, measurement_2
	 * }</pre></blockquote><p>
	 * Note that while single serie queries are possible with this method, the responsibility of
	 * unwrapping the result falls then on the caller. For a more convenient way of executing
	 * single serie queries see {@link #query(String)}.
	 *
	 * @param query the query to execute
	 *
	 * @return the query result
	 *
	 * @throws IOException if query execution fails
	 * @see #query(String)
	 * @see #batchQuery(String)
	 */
	public QueryResult queryMultipleSeries(String query) throws IOException {
		if (query.contains(";")) {
			throw new IllegalArgumentException("Query cannot contain multiple statements");
		}

		return batchQuery(query).getResults().get(0);
	}

	/**
	 * Executes a query and returns the result.
	 * <p>
	 * This method can be used to execute multiple queries at once, spanning one or more
	 * measurements. Queries such as the following can be executed:
	 * <p><blockquote><pre>{@code
	 * SELECT * FROM measurement_1; SELECT * FROM measurement_2, measurement_3;
	 * }</pre></blockquote><p>
	 * Note that while single statement and/or single serie queries are also possible with this
	 * method, the responsibility of unwrapping the result falls then on the caller. For more
	 * convenient ways of executing single statement and single serie queries see
	 * {@link #query(String)} and {@link #queryMultipleSeries(String)}.
	 *
	 * @param query the query to execute
	 *
	 * @return the query result
	 *
	 * @throws IOException if query execution fails
	 * @see #query(String)
	 * @see #queryMultipleSeries(String)
	 */
	public ApiResponse batchQuery(String query) throws IOException {
		if (SELECT_INTO_PATTERN.matcher(query).matches()) {
			throw new IllegalArgumentException("Cannot execute 'SELECT INTO' as query");
		}

		LOGGER.debug("Executing query: {}", query);
		Call<ApiResponse> call = service.query(query);
		ApiResponse response = call.execute().body();
		LOGGER.debug("Received response: {}", response);
		return response;
	}

	@Override
	public void close() {
		// Nothing to close for now.
	}

	/**
	 * Used to construct {@link JFluxHttpClient} instances.
	 */
	public static final class Builder {

		private String host;

		/**
		 * Initializes a new builder instance, setting the InfluxDB host URL.
		 *
		 * @param host the InfluxDB host URL, e.g. {@code http://localhost:8086}
		 */
		public Builder(String host) {
			this.host = host;
		}

		/**
		 * Constructs a new {@link JFluxHttpClient} instance from this builder's configuration.
		 *
		 * @return the new client instance
		 */
		public JFluxHttpClient build() {
			Retrofit retrofit = new Retrofit.Builder()
					.baseUrl(host)
					.addConverterFactory(new InfluxConverterFactory())
					.build();
			InfluxHttpService service = retrofit.create(InfluxHttpService.class);
			return new JFluxHttpClient(service);
		}
	}
}
