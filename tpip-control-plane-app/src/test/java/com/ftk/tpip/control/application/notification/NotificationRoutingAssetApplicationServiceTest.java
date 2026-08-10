package com.ftk.tpip.control.application.notification;

import static org.junit.jupiter.api.Assertions.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ftk.tpip.control.application.provider.CanonicalJsonService;
import com.ftk.tpip.control.configuration.NotificationDeliveryProperties;
import com.ftk.tpip.release.domain.model.*;
import com.ftk.tpip.release.domain.repository.NotificationRoutingAssetRepository;
import com.ftk.tpip.provider.domain.model.*;
import com.ftk.tpip.provider.domain.repository.ProviderEndpointRepository;
import com.ftk.tpip.shared.AssetCode;
import java.time.Instant;
import java.util.*;
import org.junit.jupiter.api.Test;

class NotificationRoutingAssetApplicationServiceTest {
    private final ObjectMapper json = new ObjectMapper();

    @Test
    void canonicalizesImmutableChannelAndRouteVersions() throws Exception {
        FakeRepository repository = new FakeRepository();
        NotificationRoutingAssetApplicationService service = service(repository, false);
        NotificationChannel channel = service.createChannel("ops-primary", "Operations", "prod", "architect");
        NotificationChannelVersion first = service.createChannelVersion(channel.id(), NotificationProviderType.WEBHOOK,
                "https://notify.example.test/events", "env://TPIP_SECRET_OPS_TOKEN",
                json.readTree("{\"timeout\":5,\"headers\":{\"X-App\":\"tpip\"}}"), "architect");
        NotificationChannelVersion second = service.createChannelVersion(channel.id(), NotificationProviderType.WEBHOOK,
                "https://notify.example.test/events", "env://TPIP_SECRET_OPS_TOKEN",
                json.readTree("{\"headers\":{\"X-App\":\"tpip\"},\"timeout\":5}"), "architect");

        assertEquals(first.contentChecksum(), second.contentChecksum());
        assertEquals("{\"headers\":{\"X-App\":\"tpip\"},\"timeout\":5}", first.configuration());
        assertEquals("prod", channel.environmentCode());

        NotificationRoute route = service.createRoute("health-alerts", "Health alerts", "prod", "architect");
        NotificationRouteVersion version = service.createRouteVersion(route.id(), 10,
                List.of("TPIP_HEALTH_ALERT_RESOLVED", "TPIP_HEALTH_ALERT_OPENED"),
                List.of(second.id(), first.id()), "architect");
        assertEquals("[\"TPIP_HEALTH_ALERT_OPENED\",\"TPIP_HEALTH_ALERT_RESOLVED\"]", version.eventTypes());
        assertEquals("[2,3]", version.channelVersionIds());

        assertEquals(NotificationAssetStatus.INACTIVE,
                service.changeChannelStatus(channel.id(), NotificationAssetStatus.INACTIVE, 0, "architect").status());
        assertEquals(NotificationAssetStatus.INACTIVE,
                service.changeRouteStatus(route.id(), NotificationAssetStatus.INACTIVE, 0, "architect").status());
    }

    @Test
    void rejectsEndpointsContainingCredentialsOrSecretQueryParameters() {
        NotificationRoutingAssetApplicationService service = service(new FakeRepository(), true);
        NotificationChannel channel = service.createChannel("ops-primary", "Operations", "prod", "architect");
        assertThrows(IllegalArgumentException.class, () -> service.createChannelVersion(channel.id(),
                NotificationProviderType.WEBHOOK, "http://user:secret@127.0.0.1/events", null,
                json.createObjectNode(), "architect"));
        assertThrows(IllegalArgumentException.class, () -> service.createChannelVersion(channel.id(),
                NotificationProviderType.WEBHOOK, "http://127.0.0.1/events?token=secret", null,
                json.createObjectNode(), "architect"));
    }

    @Test
    void rejectsInvalidEnvironmentCode() {
        assertThrows(IllegalArgumentException.class,
                () -> service(new FakeRepository(), false)
                        .createChannel("ops-primary", "Operations", "PROD", "architect"));
    }

    @Test
    void bindsPublishedProviderEndpointRevisionAndFreezesResolvedUri() {
        FakeRepository repository = new FakeRepository();
        NotificationDeliveryProperties properties = new NotificationDeliveryProperties();
        ProviderEndpoint endpoint = endpoint(99, "prod", EndpointLifecycleStatus.PUBLISHED, null);
        ProviderEndpointRepository endpoints = new FakeEndpointRepository(endpoint);
        var service = new NotificationRoutingAssetApplicationService(repository, new CanonicalJsonService(json),
                json, properties, null, new NotificationProviderGovernance(), endpoints);
        NotificationChannel channel = service.createChannel("ops-profile", "Operations", "prod", "architect");

        NotificationChannelVersion version = service.createChannelVersion(channel.id(), NotificationProviderType.WEBHOOK,
                null, 99L, null, json.createObjectNode(), null, "architect");

        assertEquals(99L, version.endpointRevisionId());
        assertEquals("https://notify.example.test/events", version.endpointUri());
    }

    private NotificationRoutingAssetApplicationService service(FakeRepository repository, boolean allowHttp) {
        NotificationDeliveryProperties properties = new NotificationDeliveryProperties();
        properties.setAllowHttpChannelEndpoints(allowHttp);
        return new NotificationRoutingAssetApplicationService(repository, new CanonicalJsonService(json), json, properties);
    }

    private static ProviderEndpoint endpoint(long id, String environment, EndpointLifecycleStatus status,
            Long credentialId) {
        Instant now = Instant.now();
        return new ProviderEndpoint(id, 7, AssetCode.of("notify.endpoint"), environment, 1,
                EndpointScheme.HTTPS, "https://notify.example.test", "/events", EndpointHttpMethod.POST,
                "application/json", "UTF-8", 1000, 2000, 3000, credentialId, null, null, status,
                "a".repeat(64), status == EndpointLifecycleStatus.PUBLISHED ? now : null, now);
    }

    private record FakeEndpointRepository(ProviderEndpoint endpoint) implements ProviderEndpointRepository {
        public Optional<ProviderEndpoint> findById(long id) { return endpoint.id() == id ? Optional.of(endpoint) : Optional.empty(); }
        public List<ProviderEndpoint> findAll(ProviderEndpointQuery query) { return List.of(endpoint); }
        public long count(ProviderEndpointQuery query) { return 1; }
        public boolean credentialMatchesEndpoint(long contractId,long credentialRefId,String environmentCode){return false;}
        public ProviderEndpoint createRevision(ProviderEndpoint value,String actor){throw new UnsupportedOperationException();}
        public ProviderEndpoint publish(long endpointId,String actor){throw new UnsupportedOperationException();}
    }

    private static final class FakeRepository implements NotificationRoutingAssetRepository {
        private long sequence;
        private final Map<Long, NotificationChannel> channels = new HashMap<>();
        private final Map<Long, NotificationChannelVersion> channelVersions = new HashMap<>();
        private final Map<Long, NotificationRoute> routes = new HashMap<>();
        private final Map<Long, NotificationRouteVersion> routeVersions = new HashMap<>();
        public NotificationChannel createChannel(NotificationChannel v,String a){long id=++sequence;var x=new NotificationChannel(id,v.channelCode(),v.channelName(),v.environmentCode(),v.status(),null,0,Instant.now(),Instant.now());channels.put(id,x);return x;}
        public Optional<NotificationChannel> findChannel(long id){return Optional.ofNullable(channels.get(id));}
        public List<NotificationChannel> findChannels(){return List.copyOf(channels.values());}
        public NotificationChannelVersion createChannelVersion(NotificationChannelVersion v,String a){long id=++sequence;var x=new NotificationChannelVersion(id,v.channelId(),(int)channelVersions.values().stream().filter(y->y.channelId()==v.channelId()).count()+1,v.providerType(),v.endpointUri(),v.endpointRevisionId(),v.authorizationSecretRef(),v.configuration(),v.contentChecksum(),v.templateVersionId(),NotificationAssetLifecycle.DRAFT,null,Instant.now());channelVersions.put(id,x);return x;}
        public Optional<NotificationChannelVersion> findChannelVersion(long c,long v){return Optional.ofNullable(channelVersions.get(v)).filter(x->x.channelId()==c);}
        public List<NotificationChannelVersion> findChannelVersions(long c){return channelVersions.values().stream().filter(x->x.channelId()==c).toList();}
        public NotificationChannelVersion publishChannelVersion(long c,long v,String a){return findChannelVersion(c,v).orElseThrow();}
        public NotificationChannel changeChannelStatus(long id,NotificationAssetStatus s,long rv,String a){var v=channels.get(id);var x=new NotificationChannel(id,v.channelCode(),v.channelName(),v.environmentCode(),s,v.currentVersionId(),rv+1,v.createdAt(),Instant.now());channels.put(id,x);return x;}
        public NotificationRoute createRoute(NotificationRoute v,String a){long id=++sequence;var x=new NotificationRoute(id,v.routeCode(),v.routeName(),v.environmentCode(),v.status(),null,0,Instant.now(),Instant.now());routes.put(id,x);return x;}
        public Optional<NotificationRoute> findRoute(long id){return Optional.ofNullable(routes.get(id));}
        public List<NotificationRoute> findRoutes(){return List.copyOf(routes.values());}
        public NotificationRouteVersion createRouteVersion(NotificationRouteVersion v,String a){long id=++sequence;var x=new NotificationRouteVersion(id,v.routeId(),1,v.priority(),v.eventTypes(),v.channelVersionIds(),v.contentChecksum(),NotificationAssetLifecycle.DRAFT,null,Instant.now());routeVersions.put(id,x);return x;}
        public Optional<NotificationRouteVersion> findRouteVersion(long r,long v){return Optional.ofNullable(routeVersions.get(v)).filter(x->x.routeId()==r);}
        public List<NotificationRouteVersion> findRouteVersions(long r){return routeVersions.values().stream().filter(x->x.routeId()==r).toList();}
        public NotificationRouteVersion publishRouteVersion(long r,long v,String a){return findRouteVersion(r,v).orElseThrow();}
        public NotificationRoute changeRouteStatus(long id,NotificationAssetStatus s,long rv,String a){var v=routes.get(id);var x=new NotificationRoute(id,v.routeCode(),v.routeName(),v.environmentCode(),s,v.currentVersionId(),rv+1,v.createdAt(),Instant.now());routes.put(id,x);return x;}
    }
}
