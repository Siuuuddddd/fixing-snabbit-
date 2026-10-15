package com.snabbit.prototype.gateway

import org.springframework.cloud.gateway.filter.GatewayFilterChain
import org.springframework.cloud.gateway.filter.GlobalFilter
import org.springframework.core.Ordered
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken
import org.springframework.stereotype.Component
import org.springframework.web.server.ServerWebExchange
import reactor.core.publisher.Mono

@Component
class AuthenticatedIdentityHeaderFilter : GlobalFilter, Ordered {
    override fun filter(exchange: ServerWebExchange, chain: GatewayFilterChain): Mono<Void> =
        exchange.getPrincipal<JwtAuthenticationToken>()
            .map { authentication -> mutate(exchange, authentication) }
            .defaultIfEmpty(mutate(exchange, null))
            .flatMap(chain::filter)

    private fun mutate(exchange: ServerWebExchange, authentication: JwtAuthenticationToken?): ServerWebExchange = exchange.mutate()
        .request { request ->
            request.headers { headers ->
                headers.remove(AUTHENTICATED_USER_HEADER)
                headers.remove(AUTHENTICATED_WORKER_HEADER)
                headers.remove(AUTHENTICATED_STAFF_HEADER)
                headers.remove(AUTHENTICATED_STAFF_ROLES_HEADER)
                headers.remove(AUTHENTICATED_SESSION_HEADER)
                val roles = authentication?.token?.getClaimAsStringList("roles").orEmpty()
                if ("CUSTOMER" in roles) headers.set(AUTHENTICATED_USER_HEADER, authentication!!.token.subject)
                if ("WORKER" in roles) headers.set(AUTHENTICATED_WORKER_HEADER, authentication!!.token.subject)
                if (roles.any(STAFF_ROLES::contains)) {
                    headers.set(AUTHENTICATED_STAFF_HEADER, authentication!!.token.subject)
                    headers.set(AUTHENTICATED_STAFF_ROLES_HEADER, roles.filter(STAFF_ROLES::contains).joinToString(","))
                }
                authentication?.token?.getClaimAsString("sid")?.let { headers.set(AUTHENTICATED_SESSION_HEADER, it) }
            }
        }
        .build()

    override fun getOrder(): Int = Ordered.HIGHEST_PRECEDENCE

    companion object {
        const val AUTHENTICATED_USER_HEADER = "X-Authenticated-User-Id"
        const val AUTHENTICATED_WORKER_HEADER = "X-Authenticated-Worker-Id"
        const val AUTHENTICATED_STAFF_HEADER = "X-Authenticated-Staff-Id"
        const val AUTHENTICATED_STAFF_ROLES_HEADER = "X-Authenticated-Staff-Roles"
        const val AUTHENTICATED_SESSION_HEADER = "X-Authenticated-Session-Id"
        val STAFF_ROLES = setOf("SUPPORT_AGENT", "OPERATIONS_MANAGER", "ADMIN", "FINANCE_OPERATOR")
    }
}
