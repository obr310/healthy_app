package com.example.healthy.ui.disclaimer

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * 免责声明界面
 * 用户首次使用时必须同意才能继续
 */
@Composable
fun DisclaimerScreen(
    isFirstTime: Boolean = true,
    onAgree: () -> Unit = {},
    onDecline: () -> Unit = {},
    onClose: () -> Unit = {}
) {
    val scrollState = rememberScrollState()
    
    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // 标题和图标
            Icon(
                imageVector = Icons.Default.Warning,
                contentDescription = "Warning",
                modifier = Modifier.size(64.dp),
                tint = MaterialTheme.colorScheme.error
            )
            
            Spacer(modifier = Modifier.height(16.dp))
            
            Text(
                text = "Disclaimer & Terms of Use",
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground
            )
            
            Spacer(modifier = Modifier.height(8.dp))
            
            Text(
                text = "Please read carefully before using",
                fontSize = 14.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            
            Spacer(modifier = Modifier.height(24.dp))
            
            // 免责声明内容（可滚动）
            Card(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                )
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(scrollState)
                        .padding(16.dp)
                ) {
                    DisclaimerSection(
                        title = "1. Nature of Service",
                        content = """
                            This Health Assistant AI is designed to help you track and manage your health-related activities and behaviors. It is NOT a medical device and does NOT provide medical diagnosis, treatment, or professional medical advice.
                        """.trimIndent()
                    )
                    
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    DisclaimerSection(
                        title = "2. No Medical Advice",
                        content = """
                            • The system's outputs are for informational and reference purposes only
                            • Do NOT use this system as a substitute for professional medical advice
                            • Always consult qualified healthcare professionals for medical concerns
                            • In case of emergency, contact emergency services immediately
                        """.trimIndent()
                    )
                    
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    DisclaimerSection(
                        title = "3. Data Accuracy",
                        content = """
                            • The system relies on user-provided information
                            • AI-generated responses may contain errors or inaccuracies
                            • Users are responsible for verifying information accuracy
                            • The system cannot guarantee completeness or correctness of outputs
                        """.trimIndent()
                    )
                    
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    DisclaimerSection(
                        title = "4. User Responsibility",
                        content = """
                            • You are solely responsible for your health decisions
                            • Use this system at your own risk
                            • The developers and operators assume no liability for any consequences
                            • Keep your health data private and secure
                        """.trimIndent()
                    )
                    
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    DisclaimerSection(
                        title = "5. System Limitations",
                        content = """
                            • This system has limited capabilities and knowledge
                            • It cannot handle complex medical situations
                            • It may not understand all user inputs correctly
                            • Service availability is not guaranteed
                        """.trimIndent()
                    )
                }
            }
            
            Spacer(modifier = Modifier.height(24.dp))
            
            // 按钮区域
            if (isFirstTime) {
                // 首次使用：必须同意或拒绝
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Button(
                        onClick = onAgree,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(50.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary
                        )
                    ) {
                        Text(
                            text = "I Agree and Understand",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    
                    OutlinedButton(
                        onClick = onDecline,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(50.dp)
                    ) {
                        Text(
                            text = "Decline",
                            fontSize = 16.sp
                        )
                    }
                }
            } else {
                // 再次查看：只显示关闭按钮
                Button(
                    onClick = onClose,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary
                    )
                ) {
                    Text(
                        text = "Close",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

@Composable
private fun DisclaimerSection(
    title: String,
    content: String
) {
    Column {
        Text(
            text = title,
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = content,
            fontSize = 14.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            lineHeight = 20.sp
        )
    }
}




