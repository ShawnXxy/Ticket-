package external;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import entity.Item;
import entity.Item.ItemBuilder;

public class TicketMasterAPI implements ExternalAPI {
    // http://developer.ticketmaster.com/products-and-docs/apis/discovery-api/v2/
    private static final String API_HOST = "app.ticketmaster.com";
    private static final String SEARCH_PATH = "/discovery/v2/events.json";
    private static final String DEFAULT_TERM = "ticket"; // Do not set it empty or null.
    private static final String API_KEY = "TPnKGZ8BE4pJmpRd5KEMlTH3aYfAHy8P";

    /**
     * Creates and sends a request to the TicketMaster API by term and location.
     */
//    public JSONArray search(double lat, double lon, String term) {
    public List<Item> search(double lat, double lon, String term) {
        String url = "http://" + API_HOST + SEARCH_PATH;
        // Convert geo location to geo hash with a precision of 4 (+- 20km)
        String geoHash = GeoHash.encodeGeohash(lat, lon, 4);
//        String geoPoint  = lat + "," + lon;
        if (term == null) {
            term = DEFAULT_TERM;
        }
        term = urlEncodeHelper(term);
        String query = String.format("apikey=%s&geoPoint=%s&keyword=%s", API_KEY, geoHash , term);
        try {
            /**
             * Create a URL connection instance that represents a connection to
             * the remote object referred to by the URL. The HttpUrlConnection
             * class allows to perform basic HTTP requests without the use of
             * any additional libraries. Note that this method only creates a
             * connection object, but does not establish the connection yet
             */
            HttpURLConnection connection = (HttpURLConnection) new URL(url + "?" + query).openConnection();
            connection.setRequestMethod("GET");

            /**
             * Get the status code from an HTTP response message. To execute the
             * request we can use the getRespnseCode(), connect(),
             * getInputStream(), or getOutputStream() method
             */
            int responseCode = connection.getResponseCode();
            System.out.println("\nSending 'GET' request to URL : " + url + "?" + query);
            System.out.println("Response Code : " + responseCode);

            /**
             * Create a BufferedReader to help read text from a character-inpustream. 
             * Provide for the efficient reading of characters, arrays, and lines.
             * */
            BufferedReader in = new BufferedReader(new InputStreamReader(connection.getInputStream()));
            // Append response data to response StringBuffer instance line by line
            String inputLine;
            StringBuilder response = new StringBuilder();
            while ((inputLine = in.readLine()) != null) {
                response.append(inputLine);
            }
            // Close the BufferedReader after reading the inputstream.response data
            in.close();

            // Extract events array only.
            // Create a Json object out of the response string Obtain part of the Json object - a Json array that represents events
            JSONObject responseJson = new JSONObject(response.toString());
            JSONObject embedded = (JSONObject) responseJson.get("_embedded");
            JSONArray events = (JSONArray) embedded.get("events");
//            return events;
            return getItemList(events);
        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }

    private String urlEncodeHelper(String term) {
        try {
            term = java.net.URLEncoder.encode(term, "UTF-8");
        } catch (Exception e) {
            e.printStackTrace();
        }
        return term;
    }

    private void queryAPI(double lat, double lon) {
        List<Item> itemList = search(lat, lon, null);
        try {
            for (Item item : itemList) {
                JSONObject jsonObject = item.toJSONObject();
                System.out.println(jsonObject);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }

//        JSONArray events = search(lat, lon, null);
//        try {
//            for (int i = 0; i < events.length(); i++) {
//                JSONObject event = events.getJSONObject(i);
//                System.out.println(event);
//            }
//        } catch (Exception e) {
//            e.printStackTrace();
//        }
        
    } // End of queryAPI()

    /**
     * Helper methods
     */
    // Convert JSONArray to a list of item objects.
    private List<Item> getItemList(JSONArray events) throws JSONException {
        List<Item> itemList = new ArrayList<>();

        for (int i = 0; i < events.length(); i++) {
            JSONObject event = events.getJSONObject(i);
            ItemBuilder builder = new ItemBuilder();
            builder.setItemId(getStringFieldOrNull(event, "id"));
            builder.setName(getStringFieldOrNull(event, "name"));
            builder.setDescription(getDescription(event));
            builder.setCategories(getCategories(event));
//            builder.setDate(getStringFieldOrNull(event, "date"));
//            builder.setTime(getStringFieldOrNull(event, "time"));
//            builder.setRemainingTickets(getStringFieldOrNull(event, "remaining_tickets"));
//            builder.setSaleStatus(getStringFieldOrNull(event, "sale_status"));
            builder.setImageUrl(getImageUrl(event));
            builder.setUrl(getStringFieldOrNull(event, "url"));
            JSONObject venue = getVenue(event);
            String cityName = "";
            if (venue != null) {
                if (!venue.isNull("address")) {
                    JSONObject address = venue.getJSONObject("address");
                    StringBuilder sb = new StringBuilder();
                    if (!address.isNull("line1")) {
                        sb.append(address.getString("line1"));
                    }
                    if (!address.isNull("line2")) {
                        sb.append(address.getString("line2"));
                    }
                    if (!address.isNull("line3")) {
                        sb.append(address.getString("line3"));
                    }
                    builder.setAddress(String.join(",", sb.toString(), cityName));
                }
                if (!venue.isNull("city")) {
                    JSONObject city = venue.getJSONObject("city");
                    builder.setCity(getStringFieldOrNull(city, "name"));
                }
                if (!venue.isNull("country")) {
                    JSONObject country = venue.getJSONObject("country");
                    builder.setCountry(getStringFieldOrNull(country, "name"));
                }
                if (!venue.isNull("state")) {
                    JSONObject state = venue.getJSONObject("state");
                    builder.setState(getStringFieldOrNull(state, "name"));
                }
                builder.setZipcode(getStringFieldOrNull(venue, "postalCode"));
                if (!venue.isNull("location")) {
                    JSONObject location = venue.getJSONObject("location");
                    builder.setLatitude(getNumericFieldOrNull(location, "latitude"));
                    builder.setLongitude(getNumericFieldOrNull(location, "longitude"));
                }
            }

            // Uses this builder pattern we can freely add fields.
            Item item = builder.build();
            itemList.add(item);
        }

        return itemList;
    }

    private JSONObject getVenue(JSONObject event) throws JSONException {
        if (!event.isNull("_embedded")) {
            JSONObject embedded = event.getJSONObject("_embedded");
            if (!embedded.isNull("venues")) {
                JSONArray venues = embedded.getJSONArray("venues");
                if (venues.length() >= 1) {
                    return venues.getJSONObject(0);
                }
            }
        }
        return null;
    }

    private String getImageUrl(JSONObject event) throws JSONException {
        if (!event.isNull("images")) {
            JSONArray imagesArray = event.getJSONArray("images");
            if (imagesArray.length() >= 1) {
                return getStringFieldOrNull(imagesArray.getJSONObject(0), "url" );
            }
        }
        return null;
    }

    private String getDescription(JSONObject event) throws JSONException {
        if (!event.isNull("description")) {
            return event.getString("description");
        } else if (!event.isNull("additionalInfo")) {
            return event.getString("additionalInfo");
        } else if (!event.isNull("info")) {
            return event.getString("info");
        } else if (!event.isNull("pleaseNote")) {
            return event.getString("pleaseNote");
        }
        return null;
    }

    private Set<String> getCategories(JSONObject event) throws JSONException {
        Set<String> categories = new HashSet<>();
        if (event.isNull("classifications")) {
            return categories; // Added a null checking
        }
        JSONArray classifications = (JSONArray) event.get("classifications");
        for (int j = 0; j < classifications.length(); j++) {
            JSONObject classification = classifications.getJSONObject(j);
            JSONObject segment = classification.getJSONObject("segment");
            categories.add(segment.getString("name"));
        }
        return categories;
    }

    private String getStringFieldOrNull(JSONObject event, String field) throws JSONException {
//        if (event.isNull(field)) {
//            return null;
//        } else {
//            return event.getString(field);
//        }
        return event.isNull(field) ? null : event.getString(field);
    }

    private double getNumericFieldOrNull(JSONObject event, String field) throws JSONException {
//        if (event.isNull(field)) {
//            return null;
//        } else {
//            return event.getString(field);
//        }
        return event.isNull(field) ? 0.0 : event.getDouble(field);
    }

    /**
     * Main entry for sample TicketMaster API requests.
     */
    public static void main(String[] args) {
        TicketMasterAPI tmApi = new TicketMasterAPI();
        // Mountain View, CA
        tmApi.queryAPI(37.38, -122.08);
        // London, UK
        tmApi.queryAPI(51.503364, -0.12);
        // Houston, TX
        tmApi.queryAPI(29.682684, -95.295410);
    }
}
