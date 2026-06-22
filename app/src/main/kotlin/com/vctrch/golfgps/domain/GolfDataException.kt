package com.vctrch.golfgps.domain

class GolfDataException(val error: GolfDataError) : Exception(error.name)
