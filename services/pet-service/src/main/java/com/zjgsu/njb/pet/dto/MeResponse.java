package com.zjgsu.njb.pet.dto;

import java.util.List;

/**
 * 当前身份（{@code GET /api/auth/me}）。
 *
 * <p>这个接口本身不做验签 —— 它读的是<b>网关注入的请求头</b>。
 * 能读到内容，就说明请求确实过了网关、网关验过 token 并把身份写进来了；
 * 如果直连服务端口，头不存在，接口直接 401。这是「网关信任链」最直观的证据。</p>
 *
 * @param username     网关写入的用户名
 * @param userId       网关写入的用户 id
 * @param roles        网关写入的角色列表
 * @param authenticated 固定 true：能走到这里就代表验签已通过
 */
public record MeResponse(
        String username,
        Long userId,
        List<String> roles,
        boolean authenticated
) {
}
