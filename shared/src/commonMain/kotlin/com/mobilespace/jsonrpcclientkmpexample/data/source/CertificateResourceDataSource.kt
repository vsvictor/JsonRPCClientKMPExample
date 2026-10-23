package com.mobilespace.jsonrpcclientkmpexample.data.source

import jsonrpcclientkmpexample.shared.generated.resources.Res

fun interface CertificateResourceDataSource {
    suspend fun read(name: String): ByteArray
}

class ComposeCertificateResourceDataSource : CertificateResourceDataSource {
    override suspend fun read(name: String): ByteArray = Res.readBytes("files/$name")
}
