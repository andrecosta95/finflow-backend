package br.com.finflow.auth.dto

import br.com.finflow.auth.model.User
import java.util.UUID

data class AuthResponse(
    val accessToken: String,
    val user: UserDto
)

data class UserDto(
    val id: UUID,
    val email: String,
    val name: String,
    val avatarUrl: String?,
    val plan: String
) {
    companion object {
        fun from(user: User) = UserDto(
            id = user.id,
            email = user.email,
            name = user.name,
            avatarUrl = user.avatarUrl,
            plan = user.plan.name
        )
    }
}

data class RefreshRequest(
    val refreshToken: String? = null  // alternativa ao cookie (mobile apps)
)
