package com.snabbit.prototype.worker.exception

class WorkerNotRegistered : RuntimeException("Worker account is not registered")

class InvalidWorkerSelectionGrant : RuntimeException("Worker selection is invalid or expired")
