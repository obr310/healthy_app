package org.bupt.demoapp.dto;

public class AuthResponse {
    private boolean ok;
    private String message;
    private Long userId;
    private String userName;
    public AuthResponse() {}
    public AuthResponse(boolean ok, String message, Long userId,String userName) {
        this.ok = ok;
        this.message = message;
        this.userId = userId;
        this.userName = userName;
    }
    public boolean isOk() {
        return ok;
    }
    public void setOk(boolean ok) {
        this.ok = ok;
    }
    public String getMessage() {
        return message;
    }
    public void setMessage(String message) {
        this.message = message;
    }
    public Long getUserId() {
        return userId;
    }
    public void setUserId(Long userId) {
        this.userId = userId;
    }
    public String getUserName() {
        return userName;
    }
    public void setUserName(String userName) {
        this.userName = userName;
    }

}
