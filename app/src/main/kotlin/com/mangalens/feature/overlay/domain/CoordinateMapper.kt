package com.mangalens.feature.overlay.domain

class CoordinateMapper {
	fun map(x: Int, y: Int): Pair<Int, Int> {
		return x to y
	}
}
