package com.clwater.oss_android.manager

import android.content.Context
import android.net.Uri
import android.os.Environment
import android.util.Log
import com.alibaba.sdk.android.oss.ClientException
import com.alibaba.sdk.android.oss.OSS
import com.alibaba.sdk.android.oss.OSSClient
import com.alibaba.sdk.android.oss.ServiceException
import com.alibaba.sdk.android.oss.callback.OSSCompletedCallback
import com.alibaba.sdk.android.oss.callback.OSSProgressCallback
import com.alibaba.sdk.android.oss.common.OSSLog
import com.alibaba.sdk.android.oss.common.auth.OSSAuthCredentialsProvider
import com.alibaba.sdk.android.oss.model.*
import com.clwater.oss_android.Constants
import com.clwater.oss_android.R
import com.google.gson.Gson
import java.io.File
import java.io.FileOutputStream


/**
 * @author: gengzhibo
 * @date: 2022/3/17
 */
object ALiOssManager {
    //是否完成
    private var isCompleted = false
    //本地存储路径
    var path : String = ""

    private lateinit var oss: OSS
    fun init(context: Context){
        //获取本地路径
        path = Environment.getExternalStorageDirectory().absolutePath + "/" + context.resources.getString(R.string.app_name)
        checkFile()
        val credentialProvider = OSSAuthCredentialsProvider(Constants.STS_SERVER_URL)
        oss = OSSClient(context, Constants.endpoint, credentialProvider)
    }

    //检查本地路径是否存在,
    private fun checkFile(){
        val file = File(path)
        if (!file.exists()){
            file.mkdir()
        }
    }

    //上传图片
    fun upload(uri: Uri, path: String, name: String, callback: UploadCallBack){
        val put = PutObjectRequest(
            Constants.BUCKET_NAME,
            (path + name),
            uri
        )

        put.progressCallback =
            OSSProgressCallback { request, currentSize, totalSize ->
                callback.onProgress(((currentSize / totalSize).toFloat()))
            }

        try {
            val putResult: PutObjectResult = oss.putObject(put)
            callback.onProgress(1f)
        } catch (e: ClientException) {
            callback.onFail()
            e.printStackTrace()
        } catch (e: ServiceException) {
            callback.onFail()
        }
    }

    // 列举一页文件。
    fun getObjectList(callback: ALiOssCallBack, marker: String, prefix: String) {
        if (marker.isEmpty()){
            isCompleted = false
        }
        val request = ListObjectsRequest(Constants.BUCKET_NAME)
        // 填写每页返回文件的最大个数。如果不设置此参数，则默认值为100，maxkeys的取值不能大于1000。
        request.maxKeys = 100
        request.marker = marker
        request.prefix = prefix
        oss.asyncListObjects(
            request,
            object : OSSCompletedCallback<ListObjectsRequest?, ListObjectsResult> {
                override fun onSuccess(request: ListObjectsRequest?, result: ListObjectsResult) {
                    callback.onResult(request, result)

                    // 最后一页。
                    if (!result.isTruncated) {
                        isCompleted = true
                        return
                    }
                }

                override fun onFailure(
                    request: ListObjectsRequest?,
                    clientException: ClientException,
                    serviceException: ServiceException
                ) {
                    isCompleted = true
                    callback.onFail(request, clientException, serviceException)
                    // 请求异常。
                    if (clientException != null) {
                        // 客户端异常，例如网络异常等。
                        clientException.printStackTrace()
                    }
                    if (serviceException != null) {
                        // 服务端异常。
                    }
                }
            })

    }

    //下载图片
    fun download(url: String, downloadCallBack: DownloadCallBack){
        val get = GetObjectRequest(Constants.BUCKET_NAME, url)
        get.setProgressListener { request, currentSize, totalSize ->
            downloadCallBack.onProgress(currentSize /1f / totalSize)
        }
        oss.asyncGetObject(get, object : OSSCompletedCallback<GetObjectRequest?, GetObjectResult> {
            override fun onSuccess(request: GetObjectRequest?, result: GetObjectResult) {
                val length = result.contentLength
                if (length > 0) {
                    val buffer = ByteArray(length.toInt())
                    var readCount = 0
                    while (readCount < length) {
                        try {
                            readCount += result.objectContent.read(
                                buffer,
                                readCount,
                                length.toInt() - readCount
                            )
                        } catch (e: Exception) {
                            OSSLog.logInfo(e.toString())
                        }
                    }
                    try {
                        val fileName = path + "/" + url.split("/").last()
                        val fout = FileOutputStream(fileName)
                        fout.write(buffer)
                        fout.close()
//                        downloadCallBack.onProgress(1f)
                    } catch (e: Exception) {
                        OSSLog.logInfo(e.toString())
                    }
                }
            }

            override fun onFailure(
                request: GetObjectRequest?, clientException: ClientException,
                serviceException: ServiceException
            ) {
                downloadCallBack.onFail()
            }
        })
    }

    interface DownloadCallBack{
        fun onProgress(progress: Float)
        fun onFail()
    }

    interface UploadCallBack{
        fun onProgress(progress: Float)
        fun onFail()
    }

    interface ALiOssCallBack{
        fun onResult(request: ListObjectsRequest?, result: ListObjectsResult)
        fun onFail(request: ListObjectsRequest?,
                   clientException: ClientException,
                   serviceException: ServiceException)
    }
}