package com.nickrammos.jflux.api;

import java.io.IOException;
import java.util.Collections;
import java.util.List;
import java.util.Set;

import com.nickrammos.jflux.api.converter.ApiResponseConverter;
import com.nickrammos.jflux.api.response.ApiResponse;
import com.nickrammos.jflux.api.response.QueryResult;
import com.nickrammos.jflux.api.response.ResponseMetadata;
import com.nickrammos.jflux.domain.Point;
import com.nickrammos.jflux.domain.Series;

import okhttp3.MediaType;
import okhttp3.ResponseBody;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.MockitoJUnitRunner;
import retrofit2.Call;
import retrofit2.Response;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@RunWith(MockitoJUnitRunner.class)
public class JFluxHttpClientTest {

    @Mock
    private InfluxHttpService httpService;

    @Mock
    private ApiResponseConverter responseConverter;

    @InjectMocks
    private JFluxHttpClient client;

    @Test
    public void ping_shouldReturnMetadata_onSuccessfulResponse() throws IOException {
        // Given
        @SuppressWarnings("unchecked")
        Call<ResponseBody> call = Mockito.mock(Call.class);
        Response<ResponseBody> responseWrapper = Response.success(null);
        when(httpService.ping()).thenReturn(call);
        when(call.execute()).thenReturn(responseWrapper);

        ResponseMetadata metadata = new ResponseMetadata.Builder().build();
        ApiResponse apiResponse = new ApiResponse.Builder().metadata(metadata).build();
        when(responseConverter.convert(responseWrapper)).thenReturn(apiResponse);

        // When
        ResponseMetadata result = client.ping();

        // Then
        assertThat(result).isEqualTo(metadata);
    }

    @Test
    public void query_shouldReturnSingleSeries() throws IOException {
        // Given
        String query = "SELECT * FROM measurement_1";

        @SuppressWarnings("unchecked")
        Call<ResponseBody> call = Mockito.mock(Call.class);
        when(httpService.query(query)).thenReturn(call);
        ResponseBody responseBody = ResponseBody.create(MediaType.get("application/json"), "");
        Response<ResponseBody> responseWrapper = Response.success(responseBody);
        when(call.execute()).thenReturn(responseWrapper);

        ApiResponse response = createResponse();
        when(responseConverter.convert(responseWrapper)).thenReturn(response);

        // When
        Series series = client.query(query);

        // Then
        assertThat(series).isEqualTo(response.getResults().get(0).getSeries().get(0));
    }

    @Test
    public void query_shouldReturnNull_ifNoResults() throws IOException {
        // Given
        String query = "SELECT * FROM non_existent_measurement";

        @SuppressWarnings("unchecked")
        Call<ResponseBody> call = Mockito.mock(Call.class);
        when(httpService.query(query)).thenReturn(call);
        ResponseBody responseBody = ResponseBody.create(MediaType.get("application/json"), "");
        Response<ResponseBody> responseWrapper = Response.success(responseBody);
        when(call.execute()).thenReturn(responseWrapper);

        QueryResult queryResult = new QueryResult.Builder().build();
        ApiResponse response =
                new ApiResponse.Builder().results(Collections.singletonList(queryResult)).build();
        when(responseConverter.convert(responseWrapper)).thenReturn(response);

        // When
        Series series = client.query(query);

        // Then
        assertThat(series).isNull();
    }

    @Test(expected = IllegalArgumentException.class)
    public void query_shouldThrowException_ifQueriesMultipleMeasurements() throws IOException {
        String query = "SELECT * FROM measurement_1, measurement_2";
        client.query(query);
    }

    @Test(expected = IllegalArgumentException.class)
    public void query_shouldThrowException_ifQueryIsMultiStatement() throws IOException {
        String query = "SELECT * FROM measurement_1; SELECT * FROM measurement_2";
        client.query(query);
    }

    @Test(expected = IllegalArgumentException.class)
    public void query_shouldThrowException_ifQueryIsSelectInto() throws IOException {
        String query = "SELECT * INTO measurement_1 FROM measurement_2";
        client.query(query);
    }

    @Test
    public void multiSeriesQuery_shouldReturnSingleResult() throws IOException {
        // Given
        String query = "SELECT * FROM measurement_1";

        @SuppressWarnings("unchecked")
        Call<ResponseBody> call = Mockito.mock(Call.class);
        when(httpService.query(query)).thenReturn(call);
        ResponseBody responseBody = ResponseBody.create(MediaType.get("application/json"), "");
        Response<ResponseBody> responseWrapper = Response.success(responseBody);
        when(call.execute()).thenReturn(responseWrapper);

        ApiResponse response = createResponse();
        when(responseConverter.convert(responseWrapper)).thenReturn(response);

        // When
        QueryResult result = client.queryMultipleSeries(query);

        // Then
        assertThat(result).isEqualTo(response.getResults().get(0));
    }

    @Test(expected = IllegalArgumentException.class)
    public void multiSeriesQuery_shouldThrowException_ifQueryIsMultiStatement() throws IOException {
        String query = "SELECT * FROM measurement_1; SELECT * FROM measurement_2";
        client.queryMultipleSeries(query);
    }

    @Test(expected = IllegalArgumentException.class)
    public void multiSeriesQuery_shouldThrowException_ifQueryIsSelectInto() throws IOException {
        String query = "SELECT * INTO measurement_1 FROM measurement_2";
        client.queryMultipleSeries(query);
    }

    @Test
    public void multiResultQuery_shouldReturnResponse() throws IOException {
        // Given
        String query = "SELECT * FROM measurement_1";

        @SuppressWarnings("unchecked")
        Call<ResponseBody> call = Mockito.mock(Call.class);
        when(httpService.query(query)).thenReturn(call);
        ResponseBody responseBody = ResponseBody.create(MediaType.get("application/json"), "");
        Response<ResponseBody> responseWrapper = Response.success(responseBody);
        when(call.execute()).thenReturn(responseWrapper);

        ApiResponse response = createResponse();
        when(responseConverter.convert(responseWrapper)).thenReturn(response);

        // When
        ApiResponse actualResponse = client.batchQuery(query);

        // Then
        assertThat(actualResponse).isEqualTo(response);
    }

    @Test(expected = IllegalArgumentException.class)
    public void multiResultQuery_shouldThrowException_ifQueryIsSelectInto() throws IOException {
        String query = "SELECT * INTO measurement_1 FROM measurement_2";
        client.batchQuery(query);
    }

    private static ApiResponse createResponse() {
        Set<String> tags = Collections.singleton("tag");
        List<Point> points = Collections.singletonList(new Point.Builder().build());
        Series series = new Series.Builder().name("series").tags(tags).points(points).build();

        QueryResult result = new QueryResult.Builder().statementId(0)
                .series(Collections.singletonList(series))
                .build();

        return new ApiResponse.Builder().results(Collections.singletonList(result)).build();
    }
}
