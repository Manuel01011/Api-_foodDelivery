package com.example.banner.backend.service

class GlobalException : Exception {
    constructor() : super()

    constructor(message: String) : super(message)
}