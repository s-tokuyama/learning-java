package app.store;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.Set;
import java.util.HashSet;

public class User {
    @JsonProperty("id")
    private String id;
    
    @JsonProperty("username")
    private String username;
    
    @JsonProperty("email")
    private String email;
    
    @JsonProperty("pass_hash")
    private String passHash;
    
    @JsonProperty("roles")
    private Set<String> roles;
    
    // デフォルトコンストラクタ（Jackson用）
    public User() {
        this.roles = new HashSet<>();
    }
    
    public User(String id, String username, String email, String passHash) {
        this.id = id;
        this.username = username;
        this.email = email;
        this.passHash = passHash;
        this.roles = new HashSet<>();
        this.roles.add("user"); // デフォルトロール
    }
    
    // Getters and Setters
    public String getId() {
        return id;
    }
    
    public void setId(String id) {
        this.id = id;
    }
    
    public String getUsername() {
        return username;
    }
    
    public void setUsername(String username) {
        this.username = username;
    }
    
    public String getEmail() {
        return email;
    }
    
    public void setEmail(String email) {
        this.email = email;
    }
    
    public String getPassHash() {
        return passHash;
    }
    
    public void setPassHash(String passHash) {
        this.passHash = passHash;
    }
    
    public Set<String> getRoles() {
        return roles;
    }
    
    public void setRoles(Set<String> roles) {
        this.roles = roles;
    }
    
    public void addRole(String role) {
        this.roles.add(role);
    }
    
    public boolean hasRole(String role) {
        return this.roles.contains(role);
    }
    
    public boolean isAdmin() {
        return hasRole("admin");
    }
    
    @Override
    public String toString() {
        return "User{" +
                "id='" + id + '\'' +
                ", username='" + username + '\'' +
                ", email='" + email + '\'' +
                ", roles=" + roles +
                '}';
    }
}
