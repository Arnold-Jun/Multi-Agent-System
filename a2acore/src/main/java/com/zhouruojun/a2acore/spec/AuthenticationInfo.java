package com.zhouruojun.a2acore.spec;

import java.io.Serializable;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.ToString;

/**
 * <p>
 * AuthenticationInfo
 * <a href="https://github.com/google/A2A/blob/main/docs/specification.md#69-authenticationinfo-object"></a>
 * </p>
 *
 */
@NoArgsConstructor
@AllArgsConstructor
@ToString
@Data
public class AuthenticationInfo implements Serializable {

//    model_config = ConfigDict(extra="allow")

    private List<String> schemes;
    @Nullable
    private String credentials;

}


