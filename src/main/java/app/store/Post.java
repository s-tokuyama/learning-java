package app.store;

import com.fasterxml.jackson.annotation.JsonProperty;

public class Post {
    @JsonProperty("id")
    private String id;
    
    @JsonProperty("message")
    private String message;
    
    @JsonProperty("created")
    private long created;
    
    @JsonProperty("userId")
    private String userId;
    
    // デフォルトコンストラクタ（Jackson用）
    public Post() {}
    
    public Post(String id, String message, long created, String userId) {
        this.id = id;
        this.message = message;
        this.created = created;
        this.userId = userId;
    }
    
    // Getters and Setters
    public String getId() {
        return id;
    }
    
    public void setId(String id) {
        this.id = id;
    }
    
    public String getMessage() {
        return message;
    }
    
    public void setMessage(String message) {
        this.message = message;
    }
    
    public long getCreated() {
        return created;
    }
    
    public void setCreated(long created) {
        this.created = created;
    }
    
    public String getUserId() {
        return userId;
    }
    
    public void setUserId(String userId) {
        this.userId = userId;
    }
    
    @Override
    public String toString() {
        return "Post{" +
                "id='" + id + '\'' +
                ", message='" + message + '\'' +
                ", created=" + created +
                ", userId='" + userId + '\'' +
                '}';
    }
}
