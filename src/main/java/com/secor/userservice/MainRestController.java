package com.secor.userservice;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("api/v1")
public class MainRestController {


    private static final Logger log = LoggerFactory.getLogger(MainRestController.class);


    @Autowired
    UserDetailRepository userDetailRepository;

    @Autowired
    AuthService authService;

    @PostMapping("update/user/details")
    public ResponseEntity<?> updateUserDetails(@RequestBody UserDetail userDetail,
                                               @RequestHeader("Authorization") String token)
    {
        log.info("Received request to update user details: {}", userDetail);
        if(!authService.validateToken(token))
        {
            log.info("Invalid token: {}", token);
            return ResponseEntity.badRequest().body("Invalid token");
        }
        log.info("Token is valid: {}", token);
        log.info("Updating user details: {}", userDetail);
        userDetailRepository.save(userDetail);
        return ResponseEntity.ok(userDetail);
    }



}
