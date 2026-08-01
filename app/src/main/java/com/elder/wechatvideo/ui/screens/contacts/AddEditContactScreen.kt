package com.elder.wechatvideo.ui.screens.contacts

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.text.KeyboardOptions
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.elder.wechatvideo.R
import com.elder.wechatvideo.ui.components.AvatarView
import com.elder.wechatvideo.ui.theme.AvatarGradients

/**
 * 添加 / 编辑联系人页面。
 *
 * @param contactId 编辑模式下的联系人 ID，添加模式传 null
 * @param onSaved 保存或删除完成后的返回回调
 * @param onBack 顶部返回回调
 * @param viewModel 通过 Hilt 注入的表单 ViewModel
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddEditContactScreen(
    contactId: Long?,
    onSaved: () -> Unit,
    onBack: () -> Unit,
    viewModel: AddEditContactViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    // 操作完成（保存/删除）后返回上一页
    LaunchedEffect(uiState.finished) {
        if (uiState.finished) onSaved()
    }

    var showDeleteDialog by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        stringResource(
                            if (uiState.isEdit) R.string.edit_contact else R.string.add_contact
                        )
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.cancel)
                        )
                    }
                }
            )
        }
    ) { innerPadding ->
        if (uiState.loading) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }
        } else {
            AddEditForm(
                uiState = uiState,
                contentPadding = innerPadding,
                onNameChange = viewModel::onNameChange,
                onRemarkChange = viewModel::onRemarkChange,
                onWechatIdChange = viewModel::onWechatIdChange,
                onPhoneChange = viewModel::onPhoneChange,
                onAvatarColorChange = viewModel::onAvatarColorChange,
                onSave = { viewModel.saveContact() },
                onDeleteRequest = { showDeleteDialog = true }
            )
        }
    }

    // 删除确认对话框（仅编辑模式）
    if (showDeleteDialog && uiState.isEdit) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text(stringResource(R.string.confirm)) },
            text = {
                Text(
                    text = stringResource(R.string.delete_confirm, uiState.name.ifBlank { "·" }),
                    style = MaterialTheme.typography.bodyLarge
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showDeleteDialog = false
                        viewModel.deleteContact()
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer,
                        contentColor = MaterialTheme.colorScheme.onErrorContainer
                    )
                ) {
                    Text(stringResource(R.string.confirm))
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }
}

/**
 * 表单主体。
 */
@Composable
private fun AddEditForm(
    uiState: AddEditUiState,
    contentPadding: androidx.compose.foundation.layout.PaddingValues,
    onNameChange: (String) -> Unit,
    onRemarkChange: (String) -> Unit,
    onWechatIdChange: (String) -> Unit,
    onPhoneChange: (String) -> Unit,
    onAvatarColorChange: (Int) -> Unit,
    onSave: () -> Unit,
    onDeleteRequest: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(contentPadding)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // 头像预览
        AvatarView(
            name = uiState.name.ifBlank { "·" },
            colorIndex = uiState.avatarColorIndex,
            size = 96.dp
        )

        Spacer(modifier = Modifier.height(24.dp))

        // 姓名
        FormField(
            label = stringResource(R.string.contact_name),
            value = uiState.name,
            onValueChange = onNameChange,
            isError = uiState.nameError,
            errorMessage = stringResource(R.string.contact_name)
        )

        Spacer(modifier = Modifier.height(12.dp))

        // 微信备注名
        FormField(
            label = stringResource(R.string.contact_wechat_remark),
            value = uiState.wechatRemark,
            onValueChange = onRemarkChange,
            isError = uiState.remarkError,
            errorMessage = stringResource(R.string.contact_wechat_remark)
        )

        Spacer(modifier = Modifier.height(12.dp))

        // 微信号（选填）
        FormField(
            label = stringResource(R.string.contact_wechat_id),
            value = uiState.wechatId,
            onValueChange = onWechatIdChange,
            isError = false,
            errorMessage = null,
            placeholder = stringResource(R.string.contact_wechat_id_hint),
            keyboardType = KeyboardType.Ascii
        )

        Spacer(modifier = Modifier.height(12.dp))

        // 电话号码（选填）
        FormField(
            label = stringResource(R.string.contact_phone),
            value = uiState.phone,
            onValueChange = onPhoneChange,
            isError = false,
            errorMessage = null,
            keyboardType = KeyboardType.Phone
        )

        Spacer(modifier = Modifier.height(20.dp))

        // 头像颜色选择器
        AvatarColorPicker(
            selectedIndex = uiState.avatarColorIndex,
            onSelected = onAvatarColorChange
        )

        Spacer(modifier = Modifier.height(28.dp))

        // 保存按钮
        Button(
            onClick = onSave,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 56.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary
            )
        ) {
            Text(
                text = stringResource(R.string.contact_save),
                style = MaterialTheme.typography.titleMedium
            )
        }

        // 删除按钮（仅编辑模式）
        if (uiState.isEdit) {
            Spacer(modifier = Modifier.height(12.dp))
            OutlinedButton(
                onClick = onDeleteRequest,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 52.dp),
                colors = ButtonDefaults.outlinedButtonColors(
                    contentColor = MaterialTheme.colorScheme.error
                )
            ) {
                Icon(
                    Icons.Filled.Delete,
                    contentDescription = null,
                    modifier = Modifier.size(22.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(stringResource(R.string.contact_delete))
            }
        }

        Spacer(modifier = Modifier.height(24.dp))
    }
}

/**
 * 单个表单输入字段（带标签与错误提示）。
 */
@Composable
private fun FormField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    isError: Boolean,
    errorMessage: String?,
    placeholder: String? = null,
    keyboardType: KeyboardType = KeyboardType.Text
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = label,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(bottom = 6.dp)
        )
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 56.dp),
            placeholder = placeholder?.let { { Text(it) } },
            isError = isError,
            singleLine = true,
            textStyle = MaterialTheme.typography.bodyLarge,
            shape = RoundedCornerShape(12.dp),
            keyboardOptions = KeyboardOptions(
                keyboardType = keyboardType,
                capitalization = if (keyboardType == KeyboardType.Text)
                    KeyboardCapitalization.Words else KeyboardCapitalization.None
            ),
            supportingText = if (isError && errorMessage != null) {
                { Text(errorMessage) }
            } else null
        )
    }
}

/**
 * 头像颜色选择器：6 组渐变色圆，选中项显示对勾。
 */
@Composable
private fun AvatarColorPicker(
    selectedIndex: Int,
    onSelected: (Int) -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = "头像颜色",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(bottom = 10.dp)
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            AvatarGradients.forEachIndexed { index, colors ->
                ColorCircle(
                    colors = colors,
                    isSelected = index == selectedIndex,
                    onClick = { onSelected(index) }
                )
            }
        }
    }
}

/**
 * 单个颜色选择圆。
 */
@Composable
private fun ColorCircle(
    colors: List<Color>,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .size(40.dp)
            .clip(CircleShape)
            .background(brush = Brush.linearGradient(colors = colors))
            .then(
                if (isSelected) Modifier.border(
                    width = 3.dp,
                    color = MaterialTheme.colorScheme.onSurface,
                    shape = CircleShape
                ) else Modifier
            )
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        if (isSelected) {
            Icon(
                Icons.Filled.Check,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}
