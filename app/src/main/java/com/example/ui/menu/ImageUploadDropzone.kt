package com.example.ui.menu

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.CloudUpload
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.ui.theme.*
import java.util.Locale

data class SelectedImageFile(
    val uri: Uri,
    val fileName: String,
    val bytes: ByteArray,
    val mimeType: String,
    val extension: String,
    val sizeBytes: Long,
    val sizeFormatted: String
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false
        other as SelectedImageFile
        return uri == other.uri && fileName == other.fileName && sizeBytes == other.sizeBytes
    }

    override fun hashCode(): Int {
        var result = uri.hashCode()
        result = 31 * result + fileName.hashCode()
        result = 31 * result + sizeBytes.hashCode()
        return result
    }
}

fun parseImageUri(context: Context, uri: Uri, fallbackPrefix: String = "image"): Pair<SelectedImageFile?, String?> {
    return try {
        var fileName = "$fallbackPrefix.jpg"
        var fileSize = 0L

        context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            val sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE)
            if (cursor.moveToFirst()) {
                if (nameIndex != -1) fileName = cursor.getString(nameIndex) ?: fileName
                if (sizeIndex != -1) fileSize = cursor.getLong(sizeIndex)
            }
        }

        val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
            ?: return Pair(null, "Could not open selected image stream.")

        if (fileSize <= 0L) fileSize = bytes.size.toLong()

        // 5MB client-side cap limit
        val maxSizeBytes = 5 * 1024 * 1024L
        if (fileSize > maxSizeBytes || bytes.size > maxSizeBytes) {
            val sizeMb = String.format(Locale.US, "%.1f", (if (fileSize > 0) fileSize else bytes.size.toLong()) / (1024.0 * 1024.0))
            return Pair(null, "File is too large ($sizeMb MB). Maximum allowed size is 5 MB.")
        }

        val mimeType = context.contentResolver.getType(uri) ?: when {
            fileName.endsWith(".png", true) -> "image/png"
            fileName.endsWith(".webp", true) -> "image/webp"
            fileName.endsWith(".gif", true) -> "image/gif"
            else -> "image/jpeg"
        }

        val isImage = mimeType.startsWith("image/") || listOf("jpg", "jpeg", "png", "webp", "gif").any { fileName.endsWith(it, true) }
        if (!isImage) {
            return Pair(null, "Selected file is not an image. Please pick a JPG, PNG, or WebP photo.")
        }

        val ext = when {
            fileName.contains(".") -> fileName.substringAfterLast(".").lowercase(Locale.ROOT)
            mimeType == "image/png" -> "png"
            mimeType == "image/webp" -> "webp"
            mimeType == "image/gif" -> "gif"
            else -> "jpg"
        }

        val sizeFormatted = when {
            fileSize > 1024 * 1024 -> String.format(Locale.US, "%.1f MB", fileSize / (1024.0 * 1024.0))
            else -> "${fileSize / 1024} KB"
        }

        val result = SelectedImageFile(
            uri = uri,
            fileName = fileName,
            bytes = bytes,
            mimeType = mimeType,
            extension = ext,
            sizeBytes = fileSize,
            sizeFormatted = sizeFormatted
        )
        Pair(result, null)
    } catch (e: Exception) {
        Pair(null, e.localizedMessage ?: "Failed to read image file.")
    }
}

@Composable
fun ImageUploadDropzone(
    label: String,
    isRequired: Boolean,
    selectedImage: SelectedImageFile?,
    existingImageUrl: String?,
    isUploading: Boolean,
    uploadProgressText: String = "Uploading image...",
    errorMessage: String? = null,
    onImageSelected: (SelectedImageFile) -> Unit,
    onImageRemoved: () -> Unit,
    onError: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current

    // Standard Android Photo Picker (zero-permission, Play Store compliant)
    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri: Uri? ->
        if (uri != null) {
            val (file, err) = parseImageUri(context, uri, label.lowercase(Locale.ROOT).replace(" ", "_"))
            if (err != null) {
                onError(err)
            } else if (file != null) {
                onImageSelected(file)
            }
        }
    }

    val hasImage = selectedImage != null || !existingImageUrl.isNullOrBlank()
    val imageModel = selectedImage?.uri ?: existingImageUrl

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        // Label Row
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = Slate800
                )
                if (isRequired) {
                    Text(
                        text = " *",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }

            Text(
                text = "Max 5MB (JPG, PNG, WebP)",
                style = MaterialTheme.typography.labelSmall,
                color = Slate500
            )
        }

        // Dropzone / Preview Area
        Surface(
            shape = RoundedCornerShape(14.dp),
            color = if (hasImage) Slate900 else PolishSurfaceLight,
            border = BorderStroke(
                width = if (errorMessage != null) 1.5.dp else 1.dp,
                color = if (errorMessage != null) MaterialTheme.colorScheme.error else PolishBorderLight
            ),
            modifier = Modifier
                .fillMaxWidth()
                .height(150.dp)
                .clip(RoundedCornerShape(14.dp))
                .clickable(enabled = !isUploading) {
                    photoPickerLauncher.launch(
                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                    )
                }
        ) {
            Box(modifier = Modifier.fillMaxSize()) {
                if (hasImage && imageModel != null) {
                    // Image Preview
                    AsyncImage(
                        model = imageModel,
                        contentDescription = label,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )

                    // Top Right Action Buttons
                    if (!isUploading) {
                        Row(
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .padding(8.dp),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Surface(
                                shape = RoundedCornerShape(20.dp),
                                color = Color.Black.copy(alpha = 0.65f),
                                modifier = Modifier.clickable {
                                    photoPickerLauncher.launch(
                                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                    )
                                }
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Edit,
                                        contentDescription = "Replace Image",
                                        tint = Color.White,
                                        modifier = Modifier.size(13.dp)
                                    )
                                    Text(
                                        text = "Replace",
                                        color = Color.White,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Medium
                                    )
                                }
                            }

                            Surface(
                                shape = CircleShape,
                                color = Color.Black.copy(alpha = 0.65f),
                                modifier = Modifier
                                    .size(26.dp)
                                    .clickable { onImageRemoved() }
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        imageVector = Icons.Default.Close,
                                        contentDescription = "Remove Image",
                                        tint = Color.White,
                                        modifier = Modifier.size(14.dp)
                                    )
                                }
                            }
                        }
                    }

                    // Bottom file info badge
                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomStart)
                            .fillMaxWidth()
                            .background(Color.Black.copy(alpha = 0.60f))
                            .padding(horizontal = 10.dp, vertical = 5.dp)
                    ) {
                        Text(
                            text = if (selectedImage != null) {
                                "Selected: ${selectedImage.fileName} (${selectedImage.sizeFormatted})"
                            } else {
                                "Current image saved on store"
                            },
                            color = Color.White,
                            fontSize = 11.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                } else {
                    // Empty Dropzone Placeholder
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(12.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(CircleShape)
                                .background(SpruceContainerLight),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.CloudUpload,
                                contentDescription = null,
                                tint = SprucePrimary,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                        Spacer(modifier = Modifier.height(6.dp))
                        FilledTonalButton(
                            onClick = {
                                photoPickerLauncher.launch(
                                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                )
                            },
                            shape = RoundedCornerShape(10.dp),
                            colors = ButtonDefaults.filledTonalButtonColors(
                                containerColor = SpruceContainerLight,
                                contentColor = SprucePrimary
                            ),
                            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 4.dp),
                            modifier = Modifier.height(34.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.AddPhotoAlternate,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Choose Photo", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "JPG, PNG, WebP • Max 5MB",
                            style = MaterialTheme.typography.bodySmall,
                            color = Slate500,
                            fontSize = 11.sp
                        )
                    }
                }

                // Uploading spinner overlay
                if (isUploading) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color.Black.copy(alpha = 0.70f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(28.dp),
                                color = Color.White,
                                strokeWidth = 2.5.dp
                            )
                            Text(
                                text = uploadProgressText,
                                color = Color.White,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold,
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                }
            }
        }

        // Error message if any
        AnimatedVisibility(
            visible = errorMessage != null,
            enter = fadeIn() + expandVertically(),
            exit = fadeOut() + shrinkVertically()
        ) {
            if (errorMessage != null) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 4.dp, vertical = 2.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.ErrorOutline,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(14.dp)
                    )
                    Text(
                        text = errorMessage,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                        fontSize = 11.sp
                    )
                }
            }
        }
    }
}
