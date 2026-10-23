package com.mobilespace.jsonrpcclientkmpexample

import platform.Foundation.NSProcessInfo

// The iOS simulator shares the Mac network stack. Override with SIMCTL_CHILD_JSONRPC_TEST_HOST.
internal actual fun appIntegrationServerHost(): String? =
    NSProcessInfo.processInfo.environment["JSONRPC_TEST_HOST"] as? String ?: "127.0.0.1"
