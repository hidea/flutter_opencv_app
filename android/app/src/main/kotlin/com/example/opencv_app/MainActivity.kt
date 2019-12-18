package com.example.opencv_app

import androidx.annotation.NonNull;
import io.flutter.embedding.android.FlutterActivity
import io.flutter.embedding.engine.FlutterEngine
import io.flutter.plugins.GeneratedPluginRegistrant

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import android.os.Environment
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import io.flutter.plugin.common.MethodChannel
import org.opencv.android.OpenCVLoader
import org.opencv.android.Utils
import org.opencv.core.*
import org.opencv.imgproc.Imgproc
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.*

class MainActivity: FlutterActivity() {
    override fun configureFlutterEngine(@NonNull flutterEngine: FlutterEngine) {
        GeneratedPluginRegistrant.registerWith(flutterEngine);

        OpenCVLoader.initDebug()

        MethodChannel(flutterEngine.getDartExecutor(),"api.opencv.dev/opencv")
                .setMethodCallHandler { call, result ->
            when(call.method) {
                "toPerspectiveTransformation" -> {
                    val srcpath : String? = call.argument("srcPath")
                    if (srcpath == null) {
                        result.error("error", "illigal arguments", null)
                    }
                    else {
                        result.success(toPerspectiveTransformationImg(srcpath))
                    }
                }
                else -> {
                  result.notImplemented()
                }
            }
        }
    }

    private fun rotateImage(source: Bitmap, angle: Float): Bitmap? {
        val matrix = Matrix()
        matrix.postRotate(angle)
        return Bitmap.createBitmap(source, 0, 0, source.width, source.height, matrix, true)
    }

    private fun readImageFromFileWithRotate(path:String?): Bitmap? {
        try {
            val ei = ExifInterface(path)
            val orientation = ei.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_UNDEFINED)
            val options = BitmapFactory.Options()
            options.inPreferredConfig = Bitmap.Config.ARGB_8888
            val bitmap = BitmapFactory.decodeFile(path, options)
            var rotatedBitmap: Bitmap? = null
            when (orientation) {
                ExifInterface.ORIENTATION_ROTATE_90 -> rotatedBitmap = rotateImage(bitmap, 90.0f)
                ExifInterface.ORIENTATION_ROTATE_180 -> rotatedBitmap = rotateImage(bitmap, 180.0f)
                ExifInterface.ORIENTATION_ROTATE_270 -> rotatedBitmap = rotateImage(bitmap, 270.0f)
                ExifInterface.ORIENTATION_NORMAL -> rotatedBitmap = bitmap
                else -> rotatedBitmap = bitmap
            }
            return rotatedBitmap
        } catch (e: IOException) {
            e.printStackTrace()
        }
        return null
    }

    private fun saveImageToFile(img:Bitmap): String? {
        try {
            // 一時ファイルを生成
            val timeStamp: String = SimpleDateFormat("yyyyMMdd_HHmmss").format(Date())
            val storageDir: File? = getExternalFilesDir(Environment.DIRECTORY_PICTURES)
            if (storageDir == null) {
                return null;
            }

            val file = File.createTempFile(
                "JPEG_${timeStamp}_",
                ".jpeg",
                storageDir)
            if (file == null) {
                return null;
            }

            // PNGファイルで保存
            val out = FileOutputStream(file)
            img.compress(Bitmap.CompressFormat.JPEG, 100, out)
            out.flush();
            out.close();

            return file.absolutePath;
        }
        catch (e: IOException) {
            e.printStackTrace()
            return null;
        }
    }

    private fun toPerspectiveTransformationImg(srcpath: String): String? {
        // Bitmapを読み込み
        val img = readImageFromFileWithRotate(srcpath)
        // BitmapをMatに変換する
        var matSource = Mat()
        Utils.bitmapToMat(img, matSource)

        // 前処理
        var matDest = Mat()
        // グレースケール変換
        Imgproc.cvtColor(matSource, matDest, Imgproc.COLOR_BGR2GRAY)
        // 2値化
        Imgproc.threshold(matDest, matDest, 0.0, 255.0, Imgproc.THRESH_OTSU)
        // Cannyアルゴリズムを使ったエッジ検出
        var matCanny = Mat()
        Imgproc.Canny(matDest, matCanny,75.0, 200.0)
        // 膨張
        var matKernel = Imgproc.getStructuringElement(Imgproc.MORPH_RECT, Size(9.0, 9.0))
        Imgproc.dilate(matCanny, matCanny, matKernel);

        // 輪郭を取得

        var vctContours = ArrayList<MatOfPoint>()
        var hierarchy = Mat()
        Imgproc.findContours(matCanny, vctContours, hierarchy, Imgproc.RETR_LIST, Imgproc.CHAIN_APPROX_SIMPLE);

        // 面積順にソート
        vctContours.sortByDescending {
            Imgproc.contourArea(it, false)
        }

        // 最大の四角形を走査、変換元の矩形にする
        var ptSrc = Mat(4, 2, CvType.CV_32F)
        for (vctContour in vctContours) {
            // 輪郭をまるめる
            val approxCurve = MatOfPoint2f()
            val contour2f = MatOfPoint2f()
            vctContour.convertTo(contour2f, CvType.CV_32FC2)
            var arclen = Imgproc.arcLength(contour2f, true)
            Imgproc.approxPolyDP(contour2f, approxCurve, 0.025 * arclen, true)

            // 4辺の矩形なら採用
            if (approxCurve.total() == 4L) {
                for (i in 0..3) {
                    val pt = approxCurve.get(i, 0)
                    ptSrc.put(i, 0, floatArrayOf(pt[0].toFloat(), pt[1].toFloat()))
                }
                break;
            }
        }

        // 変換先の矩形（元画像の幅を最大にした名刺比率にする）
        val width = img!!.width;
        val height = (width / 1.654).toInt();
        var ptDst = Mat(4, 2, CvType.CV_32F)
        ptDst.put(0, 0, floatArrayOf(0.0f, height.toFloat()))
        ptDst.put(1, 0, floatArrayOf(width.toFloat(), height.toFloat()))
        ptDst.put(2, 0, floatArrayOf(width.toFloat(), 0.0f))
        ptDst.put(3, 0, floatArrayOf(0.0f, 0.0f))

        // 変換行列
        var matTrans = Imgproc.getPerspectiveTransform(ptSrc, ptDst)

        // 変換
        var matResult = Mat(width, height, matSource.type());
        Imgproc.warpPerspective(matSource, matResult, matTrans, Size(width.toDouble(), height.toDouble()));

        // Mat を Bitmap に変換して保存
        var imgResult = Bitmap.createBitmap(width, height, img!!.config);
        Utils.matToBitmap(matResult, imgResult)

        return saveImageToFile(imgResult)
    }
}
