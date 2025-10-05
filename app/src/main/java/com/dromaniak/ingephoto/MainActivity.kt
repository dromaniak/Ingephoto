package com.dromaniak.ingephoto

import android.content.Context
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.dromaniak.ingephoto.DeviceHelper.ServiceReadyListener
import com.google.accompanist.pager.HorizontalPager
import com.google.accompanist.pager.rememberPagerState
import com.usdk.apiservice.aidl.extprinter.AlignType
import com.usdk.apiservice.aidl.printer.OnPrintListener
import com.usdk.apiservice.aidl.printer.UPrinter
import kotlinx.coroutines.launch
import kotlin.math.ceil
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.max
import kotlin.math.min

class MainActivity : ComponentActivity(), ServiceReadyListener {
    private var printer: UPrinter? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        DeviceHelper.me().init(applicationContext)
        DeviceHelper.me().bindService()
        DeviceHelper.me().setServiceListener(this)
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                CameraCaptureScreen {
                    val bmpMono = it.toMonochromeBmpByteArray()
                    printer?.addImage(AlignType.CENTER, bmpMono)
//                    val file = File(this.filesDir, "mono_image.bmp")
//                    file.writeBytes(bmpMono)
                    printer?.feedLine(2)

                    printer?.startPrint(object : OnPrintListener.Stub() {
                        override fun onFinish() {
                            Toast.makeText(applicationContext, "Printed", Toast.LENGTH_SHORT)
                        }

                        override fun onError(error: Int) {
                            Toast.makeText(applicationContext, "Printer error", Toast.LENGTH_SHORT)
                        }
                    })
                }
            }
        }
    }

    override fun onReady(version: String?) {
        try {
            DeviceHelper.me().register(true)
            printer = DeviceHelper.me().getPrinter()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun Bitmap.toMonochromeBmpByteArray(targetWidth: Int = 320): ByteArray {
        // 1. Safe copy (convert HARDWARE → ARGB_8888)
        val safeBitmap = if (this.config == Bitmap.Config.HARDWARE || !this.isMutable) {
            this.copy(Bitmap.Config.ARGB_8888, true)
        } else {
            this
        }

        // 2. Scale while keeping aspect ratio
        val scale = targetWidth.toFloat() / safeBitmap.width
        val targetHeight = (safeBitmap.height * scale).toInt()
        val scaledBitmap = Bitmap.createScaledBitmap(safeBitmap, targetWidth, targetHeight, true)

        val width = scaledBitmap.width
        val height = scaledBitmap.height

        // 3. Get pixels
        val pixels = IntArray(width * height)
        scaledBitmap.getPixels(pixels, 0, width, 0, 0, width, height)

        // 4. Convert to grayscale array
        val gray = DoubleArray(width * height)
        for (i in pixels.indices) {
            val c = pixels[i]
            val r = (c shr 16) and 0xFF
            val g = (c shr 8) and 0xFF
            val b = c and 0xFF
            gray[i] = 0.299 * r + 0.587 * g + 0.114 * b
        }

        // 5. Floyd–Steinberg dithering (error diffusion)
        for (y in 0 until height) {
            for (x in 0 until width) {
                val i = y * width + x
                val oldPixel = gray[i]
                val newPixel = if (oldPixel < 128) 0.0 else 255.0
                gray[i] = newPixel
                val error = oldPixel - newPixel

                // Distribute error to neighbors
                if (x + 1 < width) gray[i + 1] += error * 7.0 / 16.0
                if (y + 1 < height) {
                    if (x > 0) gray[i + width - 1] += error * 3.0 / 16.0
                    gray[i + width] += error * 5.0 / 16.0
                    if (x + 1 < width) gray[i + width + 1] += error * 1.0 / 16.0
                }

                // Clamp values
                gray[i] = min(255.0, max(0.0, gray[i]))
            }
        }

        // 6. Prepare BMP headers
        val rowBytes = ceil(width / 8.0).toInt()
        val paddedRowBytes = (rowBytes + 3) / 4 * 4
        val imageSize = paddedRowBytes * height
        val fileSize = 14 + 40 + 8 + imageSize

        val stream = ByteArrayOutputStream()

        // BITMAPFILEHEADER
        stream.write(byteArrayOf('B'.code.toByte(), 'M'.code.toByte()))
        stream.write(ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN).putInt(fileSize).array())
        stream.write(ByteArray(4)) // reserved
        stream.write(
            ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN).putInt(14 + 40 + 8).array()
        ) // offset

        // BITMAPINFOHEADER
        stream.write(
            ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN).putInt(40).array()
        ) // header size
        stream.write(ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN).putInt(width).array())
        stream.write(ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN).putInt(height).array())
        stream.write(
            ByteBuffer.allocate(2).order(ByteOrder.LITTLE_ENDIAN).putShort(1).array()
        ) // planes
        stream.write(
            ByteBuffer.allocate(2).order(ByteOrder.LITTLE_ENDIAN).putShort(1).array()
        ) // 1-bit color depth
        stream.write(ByteArray(4)) // no compression
        stream.write(
            ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN).putInt(imageSize).array()
        )
        stream.write(ByteArray(16)) // unused fields

        // Color palette: black & white
        stream.write(byteArrayOf(0, 0, 0, 0))                  // black
        stream.write(byteArrayOf(255.toByte(), 255.toByte(), 255.toByte(), 0)) // white

        // 7. Write pixel data (bottom-to-top)
        for (y in height - 1 downTo 0) {
            var byte = 0
            var bitCount = 0
            for (x in 0 until width) {
                val i = y * width + x
                val bit = if (gray[i] >= 128) 1 else 0
                byte = (byte shl 1) or bit
                bitCount++
                if (bitCount == 8) {
                    stream.write(byte)
                    byte = 0
                    bitCount = 0
                }
            }
            // Write remaining bits if width not multiple of 8
            if (bitCount > 0) {
                byte = byte shl (8 - bitCount)
                stream.write(byte)
            }
            // Padding to 4-byte boundary
            repeat(paddedRowBytes - rowBytes) { stream.write(0) }
        }

        return stream.toByteArray()
    }
}

@Composable
fun CameraCaptureScreen(
    onPrint: (Bitmap) -> Unit = {}
) {
    val context = LocalContext.current
    var photoUri by remember { mutableStateOf<Uri?>(null) }
    var bitmapList by remember { mutableStateOf<List<Bitmap>>(emptyList()) }
    var isAnimating by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    val takePictureLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicture()
    ) { success ->
        if (success && photoUri != null) {
            val bmp = getBitmapFromUri(context, photoUri!!)
            bmp?.let {
                bitmapList = listOf(it) + bitmapList
            }
        }
    }

    // Load latest photos from gallery at start
    LaunchedEffect(Unit) {
        bitmapList = getLatestBitmaps(context, 10)
    }

    val pagerState = rememberPagerState(initialPage = 0)
    val offsetY = remember { androidx.compose.animation.core.Animatable(0f) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        if (bitmapList.isNotEmpty()) {
            HorizontalPager(
                state = pagerState,
                count = bitmapList.size,
                modifier = Modifier.fillMaxSize()
            ) { page ->
                val bmp = bitmapList[page]
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .clickable(enabled = !isAnimating) {
                            // Call print function
                            onPrint(bmp)

                            // Animate photo upwards
                            scope.launch {
                                isAnimating = true
                                val screenHeight =
                                    context.resources.displayMetrics.heightPixels.toFloat()
                                offsetY.snapTo(0f)
                                offsetY.animateTo(
                                    targetValue = -screenHeight,
                                    animationSpec = androidx.compose.animation.core.tween(
                                        durationMillis = 700
                                    )
                                )
                                // Remove photo after animation
                                bitmapList = bitmapList.toMutableList().apply { removeAt(page) }
                                isAnimating = false
                                offsetY.snapTo(0f)
                            }
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Image(
                        bitmap = bmp.asImageBitmap(),
                        contentDescription = "Photo",
                        modifier = Modifier
                            .fillMaxSize()
                            .offset { androidx.compose.ui.unit.IntOffset(0, offsetY.value.toInt()) }
                    )

                    // Semi-transparent overlay text
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "Click to print photo",
                            color = Color.White.copy(alpha = 0.3f),
                            style = MaterialTheme.typography.titleLarge
                        )
                    }
                }
            }
        }

        // Button to take new photo
        Button(
            onClick = {
                photoUri = createImageUri(context)
                takePictureLauncher.launch(photoUri)

                scope.launch { pagerState.scrollToPage(0) }
            },
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(16.dp)
        ) {
            Text("Take Photo")
        }
    }
}


/** Helper functions **/

fun createImageUri(context: Context): Uri? {
    val contentValues = android.content.ContentValues().apply {
        put(MediaStore.MediaColumns.DISPLAY_NAME, "photo_${System.currentTimeMillis()}.jpg")
        put(MediaStore.MediaColumns.MIME_TYPE, "image/jpeg")
        put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/MyComposeApp")
    }
    return context.contentResolver.insert(
        MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
        contentValues
    )
}

fun getBitmapFromUri(context: Context, uri: Uri): Bitmap? {
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
        val source = ImageDecoder.createSource(context.contentResolver, uri)
        ImageDecoder.decodeBitmap(source)
    } else {
        @Suppress("DEPRECATION")
        MediaStore.Images.Media.getBitmap(context.contentResolver, uri)
    }
}

fun getLatestBitmaps(context: Context, limit: Int = 10): List<Bitmap> {
    val uris = mutableListOf<Uri>()
    val projection = arrayOf(MediaStore.Images.Media._ID)
    val sortOrder = "${MediaStore.Images.Media.DATE_TAKEN} DESC LIMIT $limit"

    context.contentResolver.query(
        MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
        projection,
        null,
        null,
        sortOrder
    )?.use { cursor ->
        val idColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media._ID)
        while (cursor.moveToNext()) {
            val id = cursor.getLong(idColumn)
            val uri = Uri.withAppendedPath(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, id.toString())
            uris.add(uri)
        }
    }

    return uris.mapNotNull { getBitmapFromUri(context, it) }
}