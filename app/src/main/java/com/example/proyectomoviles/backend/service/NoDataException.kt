package com.example.banner.backend.service

class NoDataException : Exception {
    constructor() : super()

    constructor(message: String) : super(message)
}