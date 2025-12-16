package com.darkintel.crawler.util

import org.slf4j.Logger
import org.slf4j.LoggerFactory
import kotlin.reflect.KClass

fun getLogger(clazz: KClass<*>): Logger =
    LoggerFactory.getLogger(clazz.java)
