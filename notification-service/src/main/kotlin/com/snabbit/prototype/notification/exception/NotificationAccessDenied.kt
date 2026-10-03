package com.snabbit.prototype.notification.exception

import com.snabbit.prototype.notification.domain.Notification

class NotificationAccessDenied : RuntimeException("Authenticated recipient does not own this notification resource")
