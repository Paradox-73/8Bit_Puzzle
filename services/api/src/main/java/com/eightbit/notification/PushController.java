package com.eightbit.notification;

import com.eightbit.common.config.AppProperties;
import com.eightbit.common.security.AuthUser;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/push")
public class PushController {

    private final AppProperties props;
    private final PushSubscriptionRepository repo;

    public PushController(AppProperties props, PushSubscriptionRepository repo) {
        this.props = props;
        this.repo = repo;
    }

    public record Keys(String p256dh, String auth) {}
    public record SubscribeRequest(String endpoint, Keys keys) {}
    public record UnsubscribeRequest(String endpoint) {}

    @GetMapping("/vapid-public-key")
    public Map<String, String> vapidKey() {
        return Map.of("publicKey", props.getVapid().getPublicKey());
    }

    @PostMapping("/subscribe")
    @Transactional
    public Map<String, Object> subscribe(@RequestBody SubscribeRequest req,
                                         @AuthenticationPrincipal AuthUser user) {
        repo.findByEndpoint(req.endpoint()).ifPresentOrElse(existing -> {
            existing.setP256dh(req.keys().p256dh());
            existing.setAuth(req.keys().auth());
            repo.save(existing);
        }, () -> repo.save(new PushSubscription(
                user.id(), req.endpoint(), req.keys().p256dh(), req.keys().auth())));
        return Map.of("subscribed", true);
    }

    @PostMapping("/unsubscribe")
    @Transactional
    public Map<String, Object> unsubscribe(@RequestBody UnsubscribeRequest req) {
        repo.deleteByEndpoint(req.endpoint());
        return Map.of("unsubscribed", true);
    }
}
