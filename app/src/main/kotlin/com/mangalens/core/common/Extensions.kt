package com.mangalens.core.common

class FrameRateLimiter(
	private val minIntervalMs: Long
) {
	private var lastRunMs: Long = 0

	fun shouldRun(nowMs: Long): Boolean {
		val allowed = nowMs - lastRunMs >= minIntervalMs
		if (allowed) {
			lastRunMs = nowMs
		}
		return allowed
	}
}
