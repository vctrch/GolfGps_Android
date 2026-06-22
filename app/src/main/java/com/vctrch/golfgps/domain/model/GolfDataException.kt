package com.vctrch.golfgps.domain.model

class GolfDataException(val error: GolfDataError) : Exception(error.name)
