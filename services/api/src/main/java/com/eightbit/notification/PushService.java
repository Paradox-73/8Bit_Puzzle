package com.eightbit.notification;

import com.eightbit.common.config.AppProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import nl.martijndwars.webpush.Notification;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.Security;
import java.util.List;
import java.util.Map;

/**
 * Web Push (VAPID) -- the only genuinely free push channel for a PWA. If VAPID keys are not
 * configured the whole module no-ops gracefully so the app still boots in dev (build doc 10).
 */
@Service
public class PushService {

    private static final Logger log = LoggerFactory.getLogger(PushService.class);

    private final AppProperties props;
    private final PushSubscriptionRepository repo;
    private final ObjectMapper mapper = new ObjectMapper();
    private nl.martijndwars.webpush.PushService delegate;

    public PushService(AppProperties props, PushSubscriptionRepository repo) {
        this.props = props;
        this.repo = repo;
    }

    @PostConstruct
    void init() {
        if (!props.getVapid().isConfigured()) {
            log.warn("VAPID keys not set -> web push disabled. Set VAPID_PUBLIC_KEY / VAPID_PRIVATE_KEY to enable.");
            return;
        }
        if (Security.getProvider(BouncyCastleProvider.PROVIDER_NAME) == null) {
            Security.addProvider(new BouncyCastleProvider());
        }
        try {
            delegate = new nl.martijndwars.webpush.PushService(
                    props.getVapid().getPublicKey(),
                    props.getVapid().getPrivateKey(),
                    props.getVapid().getSubject());
        } catch (Exception e) {
            log.error("Failed to init web push; disabling. {}", e.getMessage());
            delegate = null;
        }
    }

    public boolean isEnabled() {
        return delegate != null;
    }

    /** Broadcast to every subscriber. Dead endpoints (404/410) are pruned. */
    @Transactional
    public int broadcast(String title, String body, String url) {
        if (delegate == null) return 0;
        String payload;
        try {
            payload = mapper.writeValueAsString(Map.of("title", title, "body", body, "url", url));
        } catch (Exception e) {
            return 0;
        }
        int sent = 0;
        List<PushSubscription> subs = repo.findAll();
        for (PushSubscription s : subs) {
            try {
                Notification n = new Notification(s.getEndpoint(), s.getP256dh(), s.getAuth(),
                        payload.getBytes(StandardCharsets.UTF_8));
                var resp = delegate.send(n);
                int code = resp.getStatusLine().getStatusCode();
                if (code == 404 || code == 410) {
                    repo.deleteById(s.getId()); // subscription expired
                } else if (code < 300) {
                    sent++;
                }
            } catch (Exception ex) {
                log.debug("push send failed for {}: {}", s.getId(), ex.getMessage());
            }
        }
        return sent;
    }
}
