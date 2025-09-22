package com.zhouruojun.agentcore.spec.message.util;

import java.util.Locale;
import java.util.UUID;


public class Uuid {

    public static String uuid4hex() {
        return UUID.randomUUID().toString().replaceAll("-", "").toLowerCase(Locale.ROOT);
    }

}
