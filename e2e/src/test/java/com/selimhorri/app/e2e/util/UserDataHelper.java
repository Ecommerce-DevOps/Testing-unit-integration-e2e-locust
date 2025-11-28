package com.selimhorri.app.e2e.util;

import java.util.HashMap;
import java.util.Map;

/**
 * Helper class for creating properly formatted user data for E2E tests
 * Ensures user data matches backend API expectations with nested credential structure
 */
public class UserDataHelper {
    
    /**
     * Creates a user request map with proper nested credential structure
     * 
     * @param firstName User's first name
     * @param lastName User's last name
     * @param email User's email
     * @param phone User's phone number
     * @param username Username for credential
     * @param password Password for credential
     * @param imageUrl Optional image URL
     * @return Properly formatted user data map
     */
    public static Map<String, Object> createUserRequest(
            String firstName,
            String lastName,
            String email,
            String phone,
            String username,
            String password,
            String imageUrl) {
        
        Map<String, Object> userRequest = new HashMap<>();
        userRequest.put("firstName", firstName);
        userRequest.put("lastName", lastName);
        userRequest.put("email", email);
        userRequest.put("phone", phone);
        
        if (imageUrl != null && !imageUrl.isEmpty()) {
            userRequest.put("imageUrl", imageUrl);
        }
        
        // Create nested credential object
        Map<String, Object> credential = new HashMap<>();
        credential.put("username", username);
        credential.put("password", password);
        credential.put("roleBasedAuthority", "ROLE_USER");
        
        userRequest.put("credential", credential);
        
        return userRequest;
    }
    
    /**
     * Creates a user request with default image URL
     */
    public static Map<String, Object> createUserRequest(
            String firstName,
            String lastName,
            String email,
            String phone,
            String username,
            String password) {
        return createUserRequest(firstName, lastName, email, phone, username, password, "https://example.com/default.jpg");
    }
}
