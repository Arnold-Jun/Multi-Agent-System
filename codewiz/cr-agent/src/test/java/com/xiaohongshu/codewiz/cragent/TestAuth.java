package com.xiaohongshu.codewiz.cragent;

import com.nimbusds.jose.jwk.JWK;
import com.nimbusds.jose.util.JSONObjectUtils;
import com.nimbusds.jwt.SignedJWT;
import com.xiaohongshu.codewiz.a2acore.core.PushNotificationAuth;
import com.xiaohongshu.codewiz.a2acore.core.PushNotificationReceiverAuth;
import com.xiaohongshu.codewiz.a2acore.core.PushNotificationSenderAuth;
import com.xiaohongshu.codewiz.a2acore.spec.TextPart;
import com.xiaohongshu.codewiz.a2acore.spec.message.util.Util;
import java.text.ParseException;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;


@Slf4j
public class TestAuth {

    public static void main(String[] args) {
        System.out.println(JSONObjectUtils.class.getName());
    }

    @Test
    public void TestPushNotificationAuth() {
        // sender generate jwks
        PushNotificationSenderAuth senderAuth = new PushNotificationSenderAuth();
        JWK jwk = senderAuth.getJwk();
        JWK publicKey = senderAuth.getPublicKey();
        Assertions.assertThat(jwk).isNotNull();
        Assertions.assertThat(publicKey).isNotNull();

        TextPart textPart = new TextPart("hello");
        String token = senderAuth.generateJwt(textPart);
        Assertions.assertThat(token).isNotNull();
        log.info("privateKey: {}, keyId: {}", jwk, jwk.getKeyID());
        log.info("publicKey: {}, keyId: {}", publicKey, publicKey.getKeyID());
        log.info("privateKey keyId == publicKey keyId: {}", jwk.getKeyID().equals(publicKey.getKeyID()));
        log.info("jwt token: {}", token);

        Map<String, List<Map<String, Object>>> jwks = Collections.singletonMap("keys", Collections.singletonList(publicKey.toJSONObject()));
        String jwksJson = Util.toJson(jwks);
        log.info("jwks.json: {}", jwksJson);

        // receiver load jwks and verify token and data
        String json = Util.toJson(textPart);
        log.info("json: {}", json);
        TextPart dataMap = Util.fromJson(json, TextPart.class);
        PushNotificationReceiverAuth receiverAuth = new PushNotificationReceiverAuth();
        receiverAuth.loadJwksJson(jwksJson);
        boolean verified = receiverAuth.verifyPushNotification(PushNotificationAuth.AUTH_HEADER_PREFIX + token, dataMap);
        Assertions.assertThat(verified).isTrue();

        try {
            SignedJWT jwt = SignedJWT.parse(token);
            String keyIdInToken = jwt.getHeader().getKeyID();
            log.info("keyIdInToken: {}, == publicKey keyId: {}", keyIdInToken, keyIdInToken.equals(publicKey.getKeyID()));
        } catch (ParseException e) {
            throw new RuntimeException(e);
        }
    }

}
