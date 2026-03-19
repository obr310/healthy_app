package com.example.healthy.ui.login

import android.util.Log
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.healthy.data.api.RetrofitClient
import com.example.healthy.data.model.AuthRequest
import com.example.healthy.util.ErrorHandler
import kotlinx.coroutines.launch
import retrofit2.HttpException

data class LoginResult(
    val userId: String,
    val userName: String
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LoginScreen(
    onLoginSuccess: (LoginResult) -> Unit = {},
    onViewDisclaimer: () -> Unit = {}
) {
    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var agreedToTerms by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        // 标题
        Text(
            text = "Health Assistant",
            fontSize = 32.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(bottom = 48.dp)
        )

        // 用户名输入框
        OutlinedTextField(
            value = username,
            onValueChange = {
                username = it
                errorMessage = null
            },
            label = { Text("Username") },
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 16.dp),
            shape = RoundedCornerShape(12.dp),
            singleLine = true,
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = MaterialTheme.colorScheme.primary,
                unfocusedBorderColor = Color.Gray.copy(alpha = 0.5f)
            )
        )

        // 密码输入框
        OutlinedTextField(
            value = password,
            onValueChange = {
                password = it
                errorMessage = null
            },
            label = { Text("Password") },
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 24.dp),
            shape = RoundedCornerShape(12.dp),
            visualTransformation = PasswordVisualTransformation(),
            singleLine = true,
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = MaterialTheme.colorScheme.primary,
                unfocusedBorderColor = Color.Gray.copy(alpha = 0.5f)
            )
        )

        // 错误消息
        errorMessage?.let { error ->
            Text(
                text = error,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(bottom = 16.dp)
            )
        }

        // 免责声明勾选框
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 24.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Checkbox(
                checked = agreedToTerms,
                onCheckedChange = { agreedToTerms = it },
                colors = CheckboxDefaults.colors(
                    checkedColor = MaterialTheme.colorScheme.primary
                )
            )
            
            Spacer(modifier = Modifier.width(8.dp))
            
            // 可点击的文本
            val annotatedText = buildAnnotatedString {
                append("I agree to the ")
                pushStringAnnotation(tag = "disclaimer", annotation = "disclaimer")
                withStyle(
                    style = SpanStyle(
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold,
                        textDecoration = TextDecoration.Underline
                    )
                ) {
                    append("Disclaimer & Terms of Use")
                }
                pop()
            }
            
            Text(
                text = annotatedText,
                fontSize = 14.sp,
                modifier = Modifier.clickable {
                    onViewDisclaimer()
                }
            )
        }

        // 登录按钮
        Button(
            onClick = {
                if (username.isBlank() || password.isBlank()) {
                    errorMessage = "Please enter username and password"
                    return@Button
                }
                if (!agreedToTerms) {
                    errorMessage = "Please agree to the Disclaimer & Terms of Use"
                    return@Button
                }
                isLoading = true
                errorMessage = null
                scope.launch {
                    try {
                        Log.d("LoginScreen", "开始登录请求: username=$username")
                        val response = RetrofitClient.authApi.login(
                            AuthRequest(username, password)
                        )
                        isLoading = false
                        Log.d("LoginScreen", "登录响应: ok=${response.ok}, message=${response.message}, userId=${response.userId}")
                        if (response.ok) {
                            // 保存认证凭证用于后续请求
                            RetrofitClient.setAuthCredentials(username, password)
                            
                            val loginResult = LoginResult(
                                userId = response.userId?.toString() ?: "",
                                userName = response.userName ?: username
                            )
                            onLoginSuccess(loginResult)
                        } else {
                            errorMessage = response.message ?: "Login failed"
                        }
                    } catch (e: Exception) {
                        isLoading = false
                        Log.e("LoginScreen", "登录失败: ${e.javaClass.simpleName}", e)
                        // 使用 ErrorHandler 获取友好的错误消息
                        errorMessage = ErrorHandler.getUserFriendlyMessage(e)
                    }
                }
            },
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp),
            shape = RoundedCornerShape(12.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.primary
            ),
            enabled = !isLoading
        ) {
            if (isLoading) {
                CircularProgressIndicator(
                    modifier = Modifier.size(24.dp),
                    color = Color.White
                )
            } else {
                Text(
                    text = "Login",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

