package org.bupt.demoapp.common;

/**
 * Message Constants for Frontend Display
 * All user-facing messages in English
 */
public class Messages {
    
    // Auth Messages
    public static final String AUTH_INVALID_INPUT = "Username/password cannot be empty";
    public static final String AUTH_REGISTER_FAILED = "Registration failed, please try again later";
    public static final String AUTH_REGISTER_SUCCESS = "Registration and login successful";
    public static final String AUTH_WRONG_CREDENTIALS = "Incorrect username or password";
    public static final String AUTH_LOGIN_SUCCESS = "Login successful";
    
    // Chat Dispatch Messages
    public static final String CHAT_INTENT_FAILED = "Sorry, I couldn't understand your message. Please try again later";
    public static final String CHAT_PROCESSING_FAILED = "Sorry, an error occurred while processing your request. Please try again later";
    public static final String CHAT_UNKNOWN_INTENT = "I'm not sure what you're asking for. Please try:\n" +
            "• Record health logs (e.g., 'I ran 5km today')\n" +
            "• Query past records (e.g., 'What did I eat yesterday?')\n" +
            "• Request summaries (e.g., 'Summarize my exercise this week')\n" +
            "• Ask health questions (e.g., 'What are the benefits of running?')";
    
    // Record Messages
    public static final String RECORD_SAVED = "Your log has been recorded";
    
    // Query Messages
    public static final String QUERY_PROMPT = "What information would you like to query?";
    
    // Summary Messages
    public static final String SUMMARY_PROMPT = "Let me summarize that for you...";
    
    // QA Messages
    public static final String QA_PROMPT = "Let me answer your health question...";
    public static final String QA_PROCESSING_ERROR = "An error occurred while processing your question. Please try again later";
    
    // Error Messages
    public static final String ERROR_SERVICE_BUSY = "Sorry, the service is temporarily busy. Please try again later";
    
    // Conversation Titles
    public static final String CONVERSATION_TITLE_PREFIX = "Conversation ";
    
    // Empty Results
    public static final String NO_RELEVANT_KNOWLEDGE = "No relevant medical knowledge found";
    public static final String NO_RELEVANT_LOGS = "No relevant personal health records found";
    public static final String KNOWLEDGE_SEARCH_FAILED = "Knowledge base search failed";
    public static final String USER_LOG_SEARCH_FAILED = "Personal log search failed";
    
    private Messages() {
        // Utility class, prevent instantiation
    }
}

