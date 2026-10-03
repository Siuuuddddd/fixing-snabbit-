package com.snabbit.prototype.user.exception

import java.util.UUID

class UserNotFound(id: UUID) : RuntimeException("User $id was not found")
