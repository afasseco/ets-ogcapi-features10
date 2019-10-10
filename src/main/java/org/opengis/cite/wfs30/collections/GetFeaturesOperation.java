package org.opengis.cite.wfs30.collections;

import static io.restassured.http.Method.GET;
import static org.opengis.cite.wfs30.EtsAssert.assertFalse;
import static org.opengis.cite.wfs30.EtsAssert.assertTrue;
import static org.opengis.cite.wfs30.SuiteAttribute.API_MODEL;
import static org.opengis.cite.wfs30.SuiteAttribute.IUT;
import static org.opengis.cite.wfs30.WFS3.GEOJSON_MIME_TYPE;
import static org.opengis.cite.wfs30.openapi3.OpenApiUtils.retrieveTestPointsForCollection;
import static org.opengis.cite.wfs30.openapi3.OpenApiUtils.retrieveTestPointsForCollections;
import static org.opengis.cite.wfs30.util.JsonUtils.collectNumberOfAllReturnedFeatures;
import static org.opengis.cite.wfs30.util.JsonUtils.findLinkByRel;
import static org.opengis.cite.wfs30.util.JsonUtils.findLinksWithSupportedMediaTypeByRel;
import static org.opengis.cite.wfs30.util.JsonUtils.findLinksWithoutRelOrType;
import static org.opengis.cite.wfs30.util.JsonUtils.findUnsupportedTypes;
import static org.opengis.cite.wfs30.util.JsonUtils.formatDate;
import static org.opengis.cite.wfs30.util.JsonUtils.formatDateRange;
import static org.opengis.cite.wfs30.util.JsonUtils.formatDateRangeWithDuration;
import static org.opengis.cite.wfs30.util.JsonUtils.hasProperty;
import static org.opengis.cite.wfs30.util.JsonUtils.parseAsDate;
import static org.opengis.cite.wfs30.util.JsonUtils.parseFeatureId;
import static org.opengis.cite.wfs30.util.JsonUtils.parseSpatialExtent;
import static org.opengis.cite.wfs30.util.JsonUtils.parseTemporalExtent;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNotNull;

import java.net.URI;
import java.net.URISyntaxException;
import java.time.Duration;
import java.time.LocalDate;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import org.opengis.cite.wfs30.CommonDataFixture;
import org.opengis.cite.wfs30.SuiteAttribute;
import org.opengis.cite.wfs30.openapi3.TestPoint;
import org.opengis.cite.wfs30.util.BBox;
import org.opengis.cite.wfs30.util.TemporalExtent;
import org.testng.ITestContext;
import org.testng.SkipException;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import com.reprezen.kaizen.oasparser.model3.OpenApi3;
import com.reprezen.kaizen.oasparser.model3.Operation;
import com.reprezen.kaizen.oasparser.model3.Parameter;
import com.reprezen.kaizen.oasparser.model3.Path;
import com.reprezen.kaizen.oasparser.model3.Schema;

import io.restassured.path.json.JsonPath;
import io.restassured.response.Response;

/**
 * @author <a href="mailto:goltz@lat-lon.de">Lyn Goltz </a>
 */
public class GetFeaturesOperation extends CommonDataFixture {

    private final Map<String, ResponseData> collectionNameAndResponse = new HashMap<>();

    private List<Map<String, Object>> collections;

    private OpenApi3 apiModel;

    private URI iut;

    @DataProvider(name = "collectionItemUris")
    public Iterator<Object[]> collectionItemUris( ITestContext testContext ) {
        List<Object[]> collectionsData = new ArrayList<>();
        for ( Map<String, Object> collection : collections ) {
            collectionsData.add( new Object[] { collection } );
        }
        return collectionsData.iterator();
    }

    @DataProvider(name = "collectionPaths")
    public Iterator<Object[]> collectionPaths( ITestContext testContext ) {
        this.iut = (URI) testContext.getSuite().getAttribute( IUT.getName() );
        List<TestPoint> testPointsForCollections = retrieveTestPointsForCollections( apiModel, iut, noOfCollections );
        List<Object[]> collectionsData = new ArrayList<>();
        for ( TestPoint testPointForCollections : testPointsForCollections ) {
            collectionsData.add( new Object[] { testPointForCollections } );
        }
        return collectionsData.iterator();
    }

    @DataProvider(name = "collectionItemUrisWithLimit")
    public Iterator<Object[]> collectionItemUrisWithLimits( ITestContext testContext ) {
        URI iut = (URI) testContext.getSuite().getAttribute( IUT.getName() );
        List<Object[]> collectionsWithLimits = new ArrayList<>();
        for ( Map<String, Object> collection : collections ) {
            String collectionName = (String) collection.get( "name" );
            List<TestPoint> testPoints = retrieveTestPointsForCollection( apiModel, iut, collectionName );
            for ( TestPoint testPoint : testPoints ) {
                Parameter limit = findParameterByName( testPoint, "limit" );
                if ( limit != null && limit.getSchema() != null ) {
                    int min = limit.getSchema().getMinimum().intValue();
                    int max = limit.getSchema().getMaximum().intValue();
                    if ( min == max ) {
                        collectionsWithLimits.add( new Object[] { collection, min } );
                    } else {
                        collectionsWithLimits.add( new Object[] { collection, min } );
                        int betweenMinAndMax = min + ( ( max - min ) / 2 );
                        collectionsWithLimits.add( new Object[] { collection, betweenMinAndMax } );
                    }
                }
            }
        }
        return collectionsWithLimits.iterator();
    }

    @DataProvider(name = "collectionItemUrisWithBboxes")
    public Iterator<Object[]> collectionItemUrisWithBboxes( ITestContext testContext ) {
        List<Object[]> collectionsWithBboxes = new ArrayList<>();
        for ( Map<String, Object> collection : collections ) {
            BBox extent = parseSpatialExtent( collection );
            if ( extent != null ) {
                collectionsWithBboxes.add( new Object[] { collection, extent } );
                // These should include test cases which cross the
                // meridian,
                collectionsWithBboxes.add( new Object[] { collection, new BBox( -1.5, 50.0, 1.5, 53.0 ) } );
                // equator,
                collectionsWithBboxes.add( new Object[] { collection, new BBox( -80.0, -5.0, -70.0, 5.0 ) } );
                // 180 longitude,
                collectionsWithBboxes.add( new Object[] { collection, new BBox( 177.0, 65.0, -177.0, 70.0 ) } );
                // and polar regions.
                collectionsWithBboxes.add( new Object[] { collection, new BBox( -180.0, 85.0, 180.0, 90.0 ) } );
                collectionsWithBboxes.add( new Object[] { collection, new BBox( -180.0, -85.0, 180.0, -90.0 ) } );
            }
        }
        return collectionsWithBboxes.iterator();
    }

    @DataProvider(name = "collectionItemUrisWithTimes")
    public Iterator<Object[]> collectionItemUrisWithTimes( ITestContext testContext ) {
        List<Object[]> collectionsWithTimes = new ArrayList<>();
        for ( Map<String, Object> collection : collections ) {
            TemporalExtent temporalExtent = parseTemporalExtent( collection );
            if ( temporalExtent != null ) {
                ZonedDateTime begin = temporalExtent.getBegin();
                ZonedDateTime end = temporalExtent.getEnd();

                Duration between = Duration.between( begin, end );
                Duration quarter = between.dividedBy( 4 );
                ZonedDateTime beginInterval = begin.plus( quarter );
                ZonedDateTime endInterval = beginInterval.plus( quarter );

                // Example 6. A date-time
                collectionsWithTimes.add( new Object[] { collection, formatDate( begin ), beginInterval, null } );
                // Example 7. A period using a start and end time
                collectionsWithTimes.add( new Object[] { collection, formatDateRange( beginInterval, endInterval ),
                                                        beginInterval, endInterval } );
                // Example 8. A period using start time and a duration
                LocalDate beginIntervalDate = beginInterval.toLocalDate();
                LocalDate endIntervalDate = beginIntervalDate.plusDays( 2 );
                collectionsWithTimes.add( new Object[] {
                                                        collection,
                                                        formatDateRangeWithDuration( beginIntervalDate, endIntervalDate ),
                                                        beginIntervalDate, endIntervalDate } );
            }
        }
        return collectionsWithTimes.iterator();
    }

    @BeforeClass
    public void retrieveRequiredInformationFromTestContext( ITestContext testContext ) {
        this.apiModel = (OpenApi3) testContext.getSuite().getAttribute( API_MODEL.getName() );
        this.collections = (List<Map<String, Object>>) testContext.getSuite().getAttribute( SuiteAttribute.COLLECTIONS.getName() );
    }

    /**
     * A.4.4.9. Validate the Get Features Operation
     *
     * a) Test Purpose: Validate that the Get Features Operation behaves as required.
     *
     * b) Pre-conditions:
     *
     * A feature collection name is provided by test A.4.4.6
     *
     * Path = /collections/{name}/items
     *
     * c) Test Method:
     *
     * DO FOR each /collections{name}/items test point
     *
     * Issue an HTTP GET request using the test point URI
     *
     * Go to test A.4.4.10
     *
     * d) References: Requirement 17
     *
     * @param testContext
     *            used to fill the FEATUREIDS, never <code>null</code>
     * @param collection
     *            the collection under test, never <code>null</code>
     */
    @Test(description = "Implements A.4.4.9. Validate the Get Features Operation (Requirement 17, 24)", groups = "getFeaturesBase", dataProvider = "collectionItemUris", dependsOnGroups = "collections", alwaysRun = true)
    public void validateTheGetFeaturesOperation( ITestContext testContext, Map<String, Object> collection ) {
        String collectionName = (String) collection.get( "name" );

        String getFeaturesUrl = findGetFeaturesUrlForGeoJson( collection );
        if ( getFeaturesUrl == null )
            throw new SkipException( "Could not find url for collection with name " + collectionName
                                     + " supporting GeoJson (type " + GEOJSON_MIME_TYPE + ")" );

        ZonedDateTime timeStampBeforeResponse = ZonedDateTime.now();
        Response response = init().baseUri( getFeaturesUrl ).accept( GEOJSON_MIME_TYPE ).when().request( GET );
        response.then().statusCode( 200 );
        ZonedDateTime timeStampAfterResponse = ZonedDateTime.now();
        ResponseData responseData = new ResponseData( response, timeStampBeforeResponse, timeStampAfterResponse );
        collectionNameAndResponse.put( collectionName, responseData );

        addFeatureIdToTestContext( testContext, collectionName, response );
    }

    /**
     * A.4.4.10. Validate the Get Features Operation Response (Test method 2, 3)
     *
     * a) Test Purpose: Validate the Get Feature Operation Response.
     *
     * b) Pre-conditions: A collection of Features has been retrieved
     *
     * c) Test Method:
     *
     * Validate that the following links are included in the response document: To itself, Alternate encodings of this
     * document in every other media type as identified by the compliance classes for this server.
     *
     * Validate that each link includes a rel and type parameter.
     *
     * d) References: Requirements 25, 26
     *
     * @param collection
     *            the collection under test, never <code>null</code>
     */
    @Test(description = "Implements A.4.4.10. Validate the Get Features Operation Response (Requirement 25, 26)", dataProvider = "collectionItemUris", dependsOnMethods = "validateTheGetFeaturesOperation", alwaysRun = true)
    public void validateTheGetFeaturesOperationResponse_Links( Map<String, Object> collection ) {
        String collectionName = (String) collection.get( "name" );
        ResponseData response = collectionNameAndResponse.get( collectionName );
        if ( response == null )
            throw new SkipException( "Could not find a response for collection with name " + collectionName );

        JsonPath jsonPath = response.jsonPath();

        List<Map<String, Object>> links = jsonPath.getList( "links" );

        // Validate that the retrieved document includes links for: Itself
        Map<String, Object> linkToSelf = findLinkByRel( links, "self" );
        assertNotNull( linkToSelf, "Feature Collection Metadata document must include a link for itself" );

        // Validate that the retrieved document includes links for: Alternate encodings of this document in
        // every other media type as identified by the compliance classes for this server.
        List<String> mediaTypesToSupport = createListOfMediaTypesToSupportForFeatureCollectionsAndFeatures( linkToSelf );
        List<Map<String, Object>> alternateLinks = findLinksWithSupportedMediaTypeByRel( links, mediaTypesToSupport,
                                                                                         "alternate" );
        List<String> typesWithoutLink = findUnsupportedTypes( alternateLinks, mediaTypesToSupport );
        assertTrue( typesWithoutLink.isEmpty(),
                    "Feature Collection Metadata document must include links for alternate encodings. Missing links for types "
                                            + typesWithoutLink );

        // Validate that each link includes a rel and type parameter.
        List<String> linksWithoutRelOrType = findLinksWithoutRelOrType( links );
        assertTrue( linksWithoutRelOrType.isEmpty(),
                    "Links for alternate encodings must include a rel and type parameter. Missing for links "
                                            + linksWithoutRelOrType );
    }

    /**
     * A.4.4.10. Validate the Get Features Operation Response (Test method 4)
     *
     * a) Test Purpose: Validate the Get Feature Operation Response.
     *
     * b) Pre-conditions: A collection of Features has been retrieved
     *
     * c) Test Method:
     *
     * If a property timeStamp is included in the response, validate that it is close to the current time.
     *
     * d) References: Requirement 27
     *
     * @param collection
     *            the collection under test, never <code>null</code>
     */
    @Test(description = "Implements A.4.4.10. Validate the Get Features Operation Response (Requirement 27)", dataProvider = "collectionItemUris", dependsOnMethods = "validateTheGetFeaturesOperation", alwaysRun = true)
    public void validateTheGetFeaturesOperationResponse_property_timeStamp( Map<String, Object> collection ) {
        String collectionName = (String) collection.get( "name" );
        ResponseData response = collectionNameAndResponse.get( collectionName );
        if ( response == null )
            throw new SkipException( "Could not find a response for collection with name " + collectionName );

        JsonPath jsonPath = response.jsonPath();

        assertTimeStamp( collectionName, jsonPath, response.timeStampBeforeResponse, response.timeStampAfterResponse,
                         true );
    }

    /**
     * A.4.4.10. Validate the Get Features Operation Response (Test method 5)
     *
     * a) Test Purpose: Validate the Get Feature Operation Response.
     *
     * b) Pre-conditions: A collection of Features has been retrieved
     *
     * c) Test Method:
     *
     * If a property numberReturned is included in the response, validate that the number is equal to the number of
     * features in the response.
     *
     * d) References: Requirement 29
     *
     * @param collection
     *            the collection under test, never <code>null</code>
     */
    @Test(description = "Implements A.4.4.10. Validate the Get Features Operation Response (Requirement 29)", dataProvider = "collectionItemUris", dependsOnMethods = "validateTheGetFeaturesOperation", alwaysRun = true)
    public void validateGetFeaturesOperationResponse_property_numberReturned( Map<String, Object> collection ) {
        String collectionName = (String) collection.get( "name" );
        ResponseData response = collectionNameAndResponse.get( collectionName );
        if ( response == null )
            throw new SkipException( "Could not find a response for collection with name " + collectionName );

        JsonPath jsonPath = response.jsonPath();

        assertNumberReturned( collectionName, jsonPath, true );
    }

    /**
     * A.4.4.10. Validate the Get Features Operation Response (Test method 6)
     *
     * a) Test Purpose: Validate the Get Feature Operation Response.
     *
     * b) Pre-conditions: A collection of Features has been retrieved
     *
     * c) Test Method:
     *
     * If a property numberMatched is included in the response, iteratively follow the next links until no next link is
     * included and count the aggregated number of features returned in all responses during the iteration. Validate
     * that the value is identical to the numberReturned stated in the initial response.
     *
     * d) References: Requirement 28
     *
     * @param collection
     *            the collection under test, never <code>null</code>
     *
     * @throws URISyntaxException
     *             if the creation of a uri fails
     */
    @Test(description = "Implements A.4.4.10. Validate the Get Features Operation Response (Requirement 28)", dataProvider = "collectionItemUris", dependsOnMethods = "validateTheGetFeaturesOperation", alwaysRun = true)
    public void validateTheGetFeaturesOperationResponse_property_numberMatched( Map<String, Object> collection )
                            throws URISyntaxException {
        String collectionName = (String) collection.get( "name" );
        ResponseData response = collectionNameAndResponse.get( collectionName );
        if ( response == null )
            throw new SkipException( "Could not find a response for collection with name " + collectionName );

        JsonPath jsonPath = response.jsonPath();

        assertNumberMatched( collectionName, jsonPath, true );
    }

    /**
     * A.4.4.11. Limit Parameter (Test method 1)
     *
     * a) Test Purpose: Validate the proper handling of the limit parameter.
     *
     * b) Pre-conditions: Tests A.4.4.9 and A.4.4.10 have completed successfully.
     *
     * c) Test Method:
     *
     * Verify that the OpenAPI document correctly describes the limit parameter for the Get Features operation.
     *
     * d) References: Requirement 18
     *
     * Expected parameter:
     * 
     * <pre>
     * name: limit
     * in: query
     * required: false
     * schema:
     *   type: integer
     *   minimum: 1
     *   maximum: 10000 (example)
     *   default: 10 (example)
     * style: form
     * explode: false
     * </pre>
     *
     * @param testPoint
     *            the test point under test, never <code>null</code>
     * 
     */
    @Test(description = "Implements A.4.4.11. Limit Parameter (Requirement 18)", dataProvider = "collectionPaths", dependsOnMethods = "validateTheGetFeaturesOperation", alwaysRun = true)
    public void limitParameter( TestPoint testPoint ) {
        Parameter limit = findParameterByName( testPoint, "limit" );

        assertNotNull( limit, "Required limit parameter for collections path '" + testPoint.getPath()
                              + "'  in OpenAPI document is missing" );

        String msg = "Expected property '%s' with value '%s' but was '%s'";

        assertEquals( limit.getName(), "limit", String.format( msg, "name", "limit", limit.getName() ) );
        assertEquals( limit.getIn(), "query", String.format( msg, "in", "query", limit.getIn() ) );
        assertFalse( isRequired( limit ), String.format( msg, "required", "false", limit.getRequired() ) );
        assertEquals( limit.getStyle(), "form", String.format( msg, "style", "form", limit.getStyle() ) );
        assertFalse( isExplode( limit ), String.format( msg, "explode", "false", limit.getExplode() ) );

        Schema schema = limit.getSchema();
        assertEquals( schema.getType(), "integer", String.format( msg, "schema -> type", "integer", schema.getType() ) );
        assertEquals( schema.getMinimum(), 1, String.format( msg, "schema -> minimum", "1", schema.getMinimum() ) );
        assertIntegerGreaterZero( schema.getMinimum(), "schema -> minimum" );
        assertIntegerGreaterZero( schema.getDefault(), "schema -> default" );
    }

    /**
     * A.4.4.11. Limit Parameter (Test method 2, 3)
     *
     * a) Test Purpose: Validate the proper handling of the limit parameter.
     *
     * b) Pre-conditions: Tests A.4.4.9 and A.4.4.10 have completed successfully.
     *
     * c) Test Method:
     *
     * Repeat Test A.4.4.9 using different values for the limit parameter.
     *
     * For each execution of Test A.4.4.9, repeat Test A.4.4.10 to validate the results.
     *
     * d) References: Requirement 19
     *
     * @param collection
     *            the collection under test, never <code>null</code>
     * @param limit
     *            limit parameter to request, never <code>null</code>
     */
    @Test(description = "Implements A.4.4.11. Limit Parameter (Requirement 19)", dataProvider = "collectionItemUrisWithLimit", dependsOnMethods = "validateTheGetFeaturesOperation", alwaysRun = true)
    public void limitParameter_requests( Map<String, Object> collection, int limit ) {
        String collectionName = (String) collection.get( "name" );

        String getFeaturesUrl = findGetFeaturesUrlForGeoJson( collection );
        if ( getFeaturesUrl.isEmpty() )
            throw new SkipException( "Could not find url for collection with name " + collectionName
                                     + " supporting GeoJson (type " + GEOJSON_MIME_TYPE + ")" );
        ZonedDateTime timeStampBeforeResponse = ZonedDateTime.now();
        Response response = init().baseUri( getFeaturesUrl ).accept( GEOJSON_MIME_TYPE ).param( "limit", limit ).when().request( GET );
        response.then().statusCode( 200 );
        ZonedDateTime timeStampAfterResponse = ZonedDateTime.now();

        JsonPath jsonPath = response.jsonPath();
        int numberOfFeatures = jsonPath.getList( "features" ).size();
        assertTrue( numberOfFeatures <= limit, "Number of features for collection with name " + collectionName
                                               + " is unexpected (was " + numberOfFeatures + "), expected are " + limit
                                               + " or less" );
        assertTimeStamp( collectionName, jsonPath, timeStampBeforeResponse, timeStampAfterResponse, false );
        assertNumberReturned( collectionName, jsonPath, false );
    }

    /**
     * A.4.4.12. Bounding Box Parameter (Test method 1)
     *
     * a) Test Purpose:Validate the proper handling of the bbox parameter.
     *
     * b) Pre-conditions: Tests A.4.4.9 and A.4.4.10 have completed successfully.
     *
     * c) Test Method:
     *
     * Verify that the OpenAPI document correctly describes the bbox parameter for the Get Features operation.
     *
     * d) References: Requirement 20
     *
     * Expected parameter:
     *
     * <pre>
     * name: bbox
     * in: query
     * required: false
     * schema:
     *   type: array
     *   minItems: 4
     *   maxItems: 6
     *   items:
     *     type: number
     * style: form
     * explode: false
     * </pre>
     *
     * @param testPoint
     *            the testPoint under test, never <code>null</code>
     */
    @Test(description = "Implements A.4.4.12. Bounding Box Parameter (Requirement 20)", dataProvider = "collectionPaths", dependsOnMethods = "validateTheGetFeaturesOperation", alwaysRun = true)
    public void boundingBoxParameter( TestPoint testPoint ) {
        Parameter bbox = findParameterByName( testPoint, "bbox" );

        assertNotNull( bbox, "Required bbox parameter for collections path '" + testPoint.getPath()
                             + "'  in OpenAPI document is missing" );

        String msg = "Expected property '%s' with value '%s' for collections path '" + testPoint.getPath()
                     + "' but was '%s'.";

        assertEquals( bbox.getName(), "bbox", String.format( msg, "name", "bbox", bbox.getName() ) );
        assertEquals( bbox.getIn(), "query", String.format( msg, "in", "query", bbox.getIn() ) );
        assertFalse( isRequired( bbox ), String.format( msg, "required", "false", bbox.getRequired() ) );
        assertEquals( bbox.getStyle(), "form", String.format( msg, "style", "form", bbox.getStyle() ) );
        assertFalse( isExplode( bbox ), String.format( msg, "explode", "false", bbox.getExplode() ) );

        Schema schema = bbox.getSchema();
        assertEquals( schema.getType(), "array", String.format( msg, "schema -> type", "array", schema.getType() ) );
        assertEquals( schema.getMinItems().intValue(), 4,
                      String.format( msg, "schema -> minItems", "4", schema.getMinItems() ) );
        assertEquals( schema.getMaxItems().intValue(), 6,
                      String.format( msg, "schema -> maxItems", "6", schema.getMaxItems() ) );

        String itemsType = schema.getItemsSchema().getType();
        assertEquals( itemsType, "number", String.format( msg, "schema -> items -> type", "number", itemsType ) );
    }

    /**
     * A.4.4.12. Bounding Box Parameter (Test method 1)
     *
     * a) Test Purpose:Validate the proper handling of the bbox parameter.
     *
     * b) Pre-conditions: Tests A.4.4.9 and A.4.4.10 have completed successfully.
     *
     * c) Test Method:
     *
     * Repeat Test A.4.4.9 using different values for the limit parameter.
     *
     * For each execution of Test A.4.4.9, repeat Test A.4.4.10 to validate the results.
     *
     * d) References: Requirement 21
     *
     * @param collection
     *            the collection under test, never <code>null</code>
     * @param bbox
     *            bbox parameter to request, never <code>null</code>
     * @throws URISyntaxException
     *             if the creation of a uri fails
     */
    @Test(description = "Implements A.4.4.12. Bounding Box Parameter (Requirement 21)", dataProvider = "collectionItemUrisWithBboxes", dependsOnMethods = "validateTheGetFeaturesOperation", alwaysRun = true)
    public void boundingBoxParameter_requests( Map<String, Object> collection, BBox bbox )
                            throws URISyntaxException {
        String collectionName = (String) collection.get( "name" );

        String getFeaturesUrl = findGetFeaturesUrlForGeoJson( collection );
        if ( getFeaturesUrl.isEmpty() )
            throw new SkipException( "Could not find url for collection with name " + collectionName
                                     + " supporting GeoJson (type " + GEOJSON_MIME_TYPE + ")" );
        ZonedDateTime timeStampBeforeResponse = ZonedDateTime.now();
        Response response = init().baseUri( getFeaturesUrl ).accept( GEOJSON_MIME_TYPE ).param( "bbox",
                                                                                                bbox.asQueryParameter() ).when().request( GET );
        response.then().statusCode( 200 );
        ZonedDateTime timeStampAfterResponse = ZonedDateTime.now();

        JsonPath jsonPath = response.jsonPath();
        assertTimeStamp( collectionName, jsonPath, timeStampBeforeResponse, timeStampAfterResponse, false );
        assertNumberReturned( collectionName, jsonPath, false );

        // TODO: assert returned features
    }

    /**
     * A.4.4.13. Time Parameter (Test method 1)
     *
     * a) Test Purpose: Validate the proper handling of the time parameter.
     *
     * b) Pre-conditions: Tests A.4.4.9 and A.4.4.10 have completed successfully.
     *
     * c) Test Method:
     *
     * Verify that the OpenAPI document correctly describes the time parameter for the Get Features operation.
     *
     * d) References: Requirement 22
     *
     * Expected parameter:
     *
     * <pre>
     * name: time
     * in: query
     * required: false
     * schema:
     *   type: string
     * style: form
     * explode: false
     * </pre>
     * 
     * @param testPoint
     *            the testPoint under test, never <code>null</code>
     */
    @Test(description = "Implements A.4.4.13. Time Parameter (Requirement 22)", dataProvider = "collectionPaths", dependsOnMethods = "validateTheGetFeaturesOperation", alwaysRun = true)
    public void timeParameter( TestPoint testPoint ) {
        Parameter time = findParameterByName( testPoint, "time" );

        assertNotNull( time, "Required time parameter for collections with path '" + testPoint.getPath()
                             + "'  in OpenAPI document is missing" );

        String msg = "Expected property '%s' with value '%s' but was '%s'";

        assertEquals( time.getName(), "time", String.format( msg, "name", "time", time.getName() ) );
        assertEquals( time.getIn(), "query", String.format( msg, "in", "query", time.getIn() ) );
        assertFalse( isRequired( time ), String.format( msg, "required", "false", time.getRequired() ) );
        assertEquals( time.getStyle(), "form", String.format( msg, "style", "form", time.getStyle() ) );
        assertFalse( isExplode( time ), String.format( msg, "explode", "false", time.getExplode() ) );

        Schema schema = time.getSchema();
        assertEquals( schema.getType(), "string", String.format( msg, "schema -> type", "string", schema.getType() ) );
    }

    /**
     * A.4.4.13. Time Parameter (Test method 2, 3)
     *
     * a) Test Purpose:Validate the proper handling of the bbox parameter.
     *
     * b) Pre-conditions: Tests A.4.4.9 and A.4.4.10 have completed successfully.
     *
     * c) Test Method:
     *
     * Repeat Test A.4.4.9 using different values for the limit parameter.
     *
     * For each execution of Test A.4.4.9, repeat Test A.4.4.10 to validate the results.
     *
     * d) References: Requirement 23
     *
     * @param collection
     *            the collection under test, never <code>null</code>
     * @param queryParameter
     *            time parameter as string to use as query parameter, never <code>null</code>
     * @param begin
     *            a {@link ZonedDateTime} or {@link LocalDate}, the begin of the interval (or instant), never
     *            <code>null</code>
     * @param end
     *            a {@link ZonedDateTime} or {@link LocalDate}, the end of the interval, never <code>null</code> if the
     *            request is an instant
     * @throws URISyntaxException
     *             if the creation of a uri fails
     *
     */
    @Test(description = "Implements A.4.4.13. Time Parameter (Requirement 23)", dataProvider = "collectionItemUrisWithTimes", dependsOnMethods = "validateTheGetFeaturesOperation", alwaysRun = true)
    public void timeParameter_requests( Map<String, Object> collection, String queryParameter, Object begin, Object end )
                            throws URISyntaxException {
        String collectionName = (String) collection.get( "name" );

        String getFeaturesUrl = findGetFeaturesUrlForGeoJson( collection );
        if ( getFeaturesUrl.isEmpty() )
            throw new SkipException( "Could not find url for collection with name " + collectionName
                                     + " supporting GeoJson (type " + GEOJSON_MIME_TYPE + ")" );
        ZonedDateTime timeStampBeforeResponse = ZonedDateTime.now();
        Response response = init().baseUri( getFeaturesUrl ).accept( GEOJSON_MIME_TYPE ).param( "time", queryParameter ).when().request( GET );
        response.then().statusCode( 200 );
        ZonedDateTime timeStampAfterResponse = ZonedDateTime.now();

        JsonPath jsonPath = response.jsonPath();
        assertTimeStamp( collectionName, jsonPath, timeStampBeforeResponse, timeStampAfterResponse, false );
        assertNumberReturned( collectionName, jsonPath, false );

        // TODO: assert returned features
    }

    private void addFeatureIdToTestContext( ITestContext testContext, String collectionName, Response response ) {
        if ( response == null )
            return;
        Map<String, String> collectionNameAndFeatureId = (Map<String, String>) testContext.getSuite().getAttribute( SuiteAttribute.FEATUREIDS.getName() );
        if ( collectionNameAndFeatureId == null ) {
            collectionNameAndFeatureId = new HashMap<>();
            testContext.getSuite().setAttribute( SuiteAttribute.FEATUREIDS.getName(), collectionNameAndFeatureId );
        }
        String featureId = parseFeatureId( response.jsonPath() );
        if ( featureId != null )
            collectionNameAndFeatureId.put( collectionName, featureId );
    }

    private void assertTimeStamp( String collectionName, JsonPath jsonPath, ZonedDateTime timeStampBeforeResponse,
                                  ZonedDateTime timeStampAfterResponse, boolean skipIfNoTimeStamp ) {
        String timeStamp = jsonPath.getString( "timeStamp" );
        if ( timeStamp == null )
            if ( skipIfNoTimeStamp )
                throw new SkipException( "Property timeStamp is not set in collection items '" + collectionName + "'" );
            else
                return;

        ZonedDateTime date = parseAsDate( timeStamp );
        assertTrue( date.isBefore( timeStampAfterResponse ),
                    "timeStamp in response must be before the request was send (" + formatDate( timeStampAfterResponse )
                                            + ") but was '" + timeStamp + "'" );
        assertTrue( date.isAfter( timeStampBeforeResponse ),
                    "timeStamp in response must be after the request was send (" + formatDate( timeStampBeforeResponse )
                                            + ") but was '" + timeStamp + "'" );
    }

    private void assertNumberReturned( String collectionName, JsonPath jsonPath, boolean skipIfNoNumberReturned ) {
        if ( !hasProperty( "numberReturned", jsonPath ) )
            if ( skipIfNoNumberReturned )
                throw new SkipException( "Property numberReturned is not set in collection items '" + collectionName
                                         + "'" );
            else
                return;

        int numberReturned = jsonPath.getInt( "numberReturned" );
        int numberOfFeatures = jsonPath.getList( "features" ).size();
        assertEquals( numberReturned, numberOfFeatures, "Value of numberReturned (" + numberReturned
                                                        + ") does not match the number of features in the response ("
                                                        + numberOfFeatures + ")" );
    }

    private void assertNumberMatched( String collectionName, JsonPath jsonPath, boolean skipIfNoNumberMatched )
                            throws URISyntaxException {
        if ( !hasProperty( "numberMatched", jsonPath ) )
            if ( skipIfNoNumberMatched )
                throw new SkipException( "Property numberMatched is not set in collection items '" + collectionName
                                         + "'" );
            else
                return;

        int maximumLimit = -1;

        TestPoint testPoint = retrieveTestPointsForCollection( apiModel, iut, collectionName ).get( 0 );
        if ( testPoint != null ) {
            Parameter limitParameter = findParameterByName( testPoint, "limit" );
            if ( limitParameter != null && limitParameter.getSchema() != null ) {
                maximumLimit = limitParameter.getSchema().getMaximum().intValue();
            }
        }
        int numberMatched = jsonPath.getInt( "numberMatched" );
        int numberOfAllReturnedFeatures = collectNumberOfAllReturnedFeatures( jsonPath, maximumLimit );
        assertEquals( numberMatched, numberOfAllReturnedFeatures,
                      "Value of numberReturned (" + numberMatched
                                              + ") does not match the number of features in all responses ("
                                              + numberOfAllReturnedFeatures + ")" );
    }

    private Parameter findParameterByName( TestPoint testPoint, String name ) {
        String collectionItemPath = testPoint.getPath();
        Path path = apiModel.getPath( collectionItemPath );
        if ( path != null ) {
            for ( Parameter parameter : path.getParameters() )
                if ( name.equals( parameter.getName() ) )
                    return parameter;
            Operation get = path.getOperation( "get" );
            for ( Parameter parameter : get.getParameters() )
                if ( name.equals( parameter.getName() ) )
                    return parameter;
        }
        return null;
    }

    private String findGetFeaturesUrlForGeoJson( Map<String, Object> collection ) {
        List<Object> links = (List<Object>) collection.get( "links" );
        for ( Object linkObject : links ) {
            Map<String, Object> link = (Map<String, Object>) linkObject;
            Object rel = link.get( "rel" );
            Object type = link.get( "type" );
            if ( "item".equals( rel ) && GEOJSON_MIME_TYPE.equals( type ) )
                return (String) link.get( "href" );
        }
        return null;
    }

    private void assertIntegerGreaterZero( Object value, String propertyName ) {
        if ( value instanceof Number )
            assertIntegerGreaterZero( ( (Number) value ).intValue(), propertyName );
        else if ( value instanceof String )
            try {
                int valueAsInt = Integer.parseInt( (String) value );
                assertIntegerGreaterZero( valueAsInt, propertyName );
            } catch ( NumberFormatException e ) {
                String msg = "Expected property '%s' to be an integer, but was '%s'";
                throw new AssertionError( String.format( msg, propertyName, value ) );
            }
    }

    private void assertIntegerGreaterZero( int value, String propertyName ) {
        String msg = "Expected property '%s' to be an integer greater than 0, but was '%s'";
        assertTrue( value > 0, String.format( msg, propertyName, value ) );
    }

    private boolean isRequired( Parameter param ) {
        return param.getRequired() != null && param.getRequired();
    }

    private Boolean isExplode(Parameter param) {
        return param.getExplode() != null && param.getExplode();
    }
    
    private class ResponseData {

        private final Response response;

        private final ZonedDateTime timeStampBeforeResponse;

        private final ZonedDateTime timeStampAfterResponse;

        public ResponseData( Response response, ZonedDateTime timeStampBeforeResponse,
                             ZonedDateTime timeStampAfterResponse ) {
            this.response = response;
            this.timeStampBeforeResponse = timeStampBeforeResponse;
            this.timeStampAfterResponse = timeStampAfterResponse;
        }

        public JsonPath jsonPath() {
            return response.jsonPath();
        }
    }

}
