package fr.totetmatt.blueskygephi.atproto;

/**
 *
 * @author totetmatt
 */
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import fr.totetmatt.blueskygephi.atproto.response.AppBskyActorGetProfile;
import fr.totetmatt.blueskygephi.atproto.response.AppBskyGraphGetFollowers;
import fr.totetmatt.blueskygephi.atproto.response.AppBskyGraphGetFollows;
import fr.totetmatt.blueskygephi.atproto.response.AppBskyGraphGetList;
import fr.totetmatt.blueskygephi.atproto.response.ComAtprotoServerCreateSession;
import java.io.IOException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.logging.Logger;
import org.openide.util.Exceptions;

public class AtClient {

    private static final Logger logger = Logger.getLogger(AtClient.class.getName());

    private final ObjectMapper objectMapper = new ObjectMapper().configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
    private final AtContext context;

    private final HttpClient client = HttpClient.newHttpClient();
    private ComAtprotoServerCreateSession session = null;

    public AtClient(String host) {
        context = new AtContext(host);
    }

    public boolean comAtprotoServerCreateSession(String identifier, String password) {
        try {
            // Build the JSON body with the mapper so a password containing
            // quotes/backslashes can't break or inject into the request.
            String body = objectMapper.writeValueAsString(Map.of(
                    "identifier", identifier,
                    "password", password));
            HttpRequest request = HttpRequest.newBuilder(context.getURIForLexicon("com.atproto.server.createSession"))
                    .POST(HttpRequest.BodyPublishers.ofString(body))
                    .header("Content-Type", "application/json")
                    .build();
            var response = client.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) {
                logger.warning("Bluesky createSession failed (HTTP " + response.statusCode() + "): " + response.body());
                this.session = null;
                return false;
            }
            this.session = objectMapper.readValue(response.body(), ComAtprotoServerCreateSession.class);
            return this.session != null && this.session.getAccessJwt() != null;
        } catch (IOException | InterruptedException e) {
            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            logger.warning("Bluesky createSession error: " + e.getMessage());
            this.session = null;
            return false;
        }
    }

    private void requireSession() {
        if (session == null || session.getAccessJwt() == null) {
            throw new IllegalStateException("Not authenticated with Bluesky. Please connect first.");
        }
    }

    private HttpRequest getRequest(String xrpcMethod, HashMap<String, String> params) {
        requireSession();
        return HttpRequest
                .newBuilder(context.getURIForLexicon(xrpcMethod, params))
                .GET()
                .header("Authorization", "Bearer " + session.getAccessJwt())
                .build();
    }

    // Yeah, it should be generalized, and async, but it works right now so it's ok.
    public List<AppBskyGraphGetFollowers> appBskyGraphGetFollowers(String actor, Optional<Integer> limitCrawl) {
        List<AppBskyGraphGetFollowers> pagedResponse = new ArrayList<>();
        try {
            var params = new HashMap<String, String>();
            params.put("actor", actor);
            params.put("limit", "100");
            int currentCrawlLoop=0;
            while (limitCrawl.isEmpty() ||currentCrawlLoop < limitCrawl.get()) {
                var request = getRequest("app.bsky.graph.getFollowers", params);
                var response = client.send(request, HttpResponse.BodyHandlers.ofString());
                if (response.statusCode() != 200) {
                    logger.warning("app.bsky.graph.getFollowers failed for " + actor + " (HTTP " + response.statusCode() + ")");
                    break;
                }
                AppBskyGraphGetFollowers objectResponse = objectMapper.readValue(response.body(), AppBskyGraphGetFollowers.class);
                pagedResponse.add(objectResponse);
                if (objectResponse.getCursor() == null) {
                    break;
                }
                params.put("cursor", objectResponse.getCursor());
                currentCrawlLoop++;
            }
            return pagedResponse;
        } catch (IOException | InterruptedException e) {
            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            throw new RuntimeException(e);
        }
    }

    public List<AppBskyGraphGetFollows> appBskyGraphGetFollows(String actor, Optional<Integer> limitCrawl) {
        List<AppBskyGraphGetFollows> pagedResponse = new ArrayList<>();
        try {
            var params = new HashMap<String, String>();
            params.put("actor", actor);
            params.put("limit", "100");
            int currentCrawlLoop=0;
            while (limitCrawl.isEmpty() || currentCrawlLoop < limitCrawl.get()) {
                var request = getRequest("app.bsky.graph.getFollows", params);
                var response = client.send(request, HttpResponse.BodyHandlers.ofString());
                if (response.statusCode() != 200) {
                    logger.warning("app.bsky.graph.getFollows failed for " + actor + " (HTTP " + response.statusCode() + ")");
                    break;
                }
                var objectResponse = objectMapper.readValue(response.body(), AppBskyGraphGetFollows.class);
                pagedResponse.add(objectResponse);
                if (objectResponse.getCursor() == null) {
                    break;
                }
                params.put("cursor", objectResponse.getCursor());
                currentCrawlLoop++;
            }
            return pagedResponse;
        } catch (IOException | InterruptedException e) {
            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            throw new RuntimeException(e);
        }
    }

    public AppBskyActorGetProfile appBskyActorGetProfile(String actor) {
        try {
            var params = new HashMap<String, String>();
            params.put("actor", actor);
            var request = getRequest("app.bsky.actor.getProfile", params);
            var response = client.send(request, HttpResponse.BodyHandlers.ofString());
            return objectMapper.readValue(response.body(), AppBskyActorGetProfile.class);
        } catch (IOException | InterruptedException ex) {
            if (ex instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            Exceptions.printStackTrace(ex);
        }
        return null;
    }
    
     public List<AppBskyGraphGetList> appBskyGraphGetList(String list) {
         List<AppBskyGraphGetList> lists = new ArrayList<>();
        try {
            var params = new HashMap<String, String>();
            params.put("list", list);
            params.put("limit", "100");
            while (true) {
                var request = getRequest("app.bsky.graph.getList", params);
                var response = client.send(request, HttpResponse.BodyHandlers.ofString());
                if (response.statusCode() != 200) {
                    logger.warning("app.bsky.graph.getList failed for " + list + " (HTTP " + response.statusCode() + ")");
                    break;
                }
                var objectResponse = objectMapper.readValue(response.body(), AppBskyGraphGetList.class);
                lists.add(objectResponse);
                if (objectResponse.getCursor() == null) {
                    break;
                }
                params.put("cursor", objectResponse.getCursor());
            }
        } catch (IOException | InterruptedException ex) {
            if (ex instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            Exceptions.printStackTrace(ex);
        }
        return lists;
    }

    public AppBskyActorGetProfile appBskyActorGetProfiles(String actors) {
        try {
            requireSession();
            var request = HttpRequest
                    .newBuilder(context.getURIForLexicon("app.bsky.actor.getProfiles", actors))
                    .GET()
                    .header("Authorization", "Bearer " + session.getAccessJwt())
                    .build();
            var response = client.send(request, HttpResponse.BodyHandlers.ofString());
            return objectMapper.readValue(response.body(), AppBskyActorGetProfile.class);
        } catch (IOException | InterruptedException ex) {
            if (ex instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            Exceptions.printStackTrace(ex);
        }
        return null;
    }
}
