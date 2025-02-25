package com.secor.jdev25authservice;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Random;

@RestController
@RequestMapping("api/v1")
public class MainRestController {


    private static final Logger log = LoggerFactory.getLogger(MainRestController.class);


    @Autowired
    private CredentialRepository credentialRepository;

    @Autowired
    @Qualifier("webClientUserService")
    private WebClient webClientUserService;

    @Autowired
    private RedisTemplate<String, Object> redisTemplate;


    @PostMapping("/signup")
    public ResponseEntity<String> signup(@RequestBody  Credential credential)
    {
        credentialRepository.save(credential); // CREATION IN DB DONE BY SPRING DATA JPA
        log.info("NEW SIGNUP SUCCESFUL FOR USER: "+credential.getUsername());
        return ResponseEntity.ok("New Signup Succesful for "+credential.getUsername());
    }

    @GetMapping("/login")
    public ResponseEntity<String> login(@RequestBody Credential credential)
    {
        if(credentialRepository.findById(credential.getUsername()).isPresent())
        {
            if(credentialRepository.findById(credential.getUsername()).get().getPassword().equals(credential.getPassword()))
            {
                log.info("LOGIN SUCCESSFUL FOR USER: "+credential.getUsername());
                String token = new Random().nextInt()+credential.getUsername();
                redisTemplate.opsForValue().set(token,"VALID");
                return ResponseEntity.ok().header("Authorization",token).body("Login Successful for "+credential.getUsername());
            }
            else
            {
                return ResponseEntity.badRequest().body("Login Failed | INCORRECT PASSWORD");
            }
        }
        else
        {
            return ResponseEntity.badRequest().body("Login Failed | INVALID USERNAME");
        }

    }

    @GetMapping("/validate")
    public ResponseEntity<String> validateToken(@RequestHeader("Authorization") String token)
    {
        if(redisTemplate.opsForValue().get(token) == null)
        {
            return ResponseEntity.badRequest().body("INVALID TOKEN");
        }
        else
        {
            return ResponseEntity.ok("VALID TOKEN");
        }
    }


    @GetMapping("/user/{username}")
    public ResponseEntity<?> user(@PathVariable("username") String username,
                                  HttpServletRequest request,
                                  HttpServletResponse response)
    {

        // COOKIE VALIDATION LOGIC
        List<Cookie> cookieList = null;
        log.info("initiating cookie check");

        //Optional<String> healthStatusCookie = Optional.ofNullable(request.getHeader("health_status_cookie"));
        Cookie[] cookies = request.getCookies();
        if(cookies == null)
        {
            cookieList = new ArrayList<>();
        }
        else
        {
            // REFACTOR TO TAKE NULL VALUES INTO ACCOUNT
            cookieList = List.of(cookies);
        }
        log.info("cookie check complete");

        if( cookieList.stream().filter(cookie -> cookie.getName().contains("auth-service-cookie-1")).findAny().isEmpty()) // COOKIE_CHECK
        {
            // FRESH REQUEST LOGIC GOES HERE
            Optional<Credential> credential = Optional.empty();
            UserDetailView userDetailView = new UserDetailView();

            if(credentialRepository.findById(username).isEmpty())
            {
                return ResponseEntity.notFound().build();
            }
            else {

                // Here goes the actual logic for composing the query response [SUCCESS SCENARIO]

                // 0. COOKIE CREATION LOGIC
                //Integer cookie_value =  new Random().nextInt();
                Cookie cookieGetUserDetails = new Cookie("auth-service-cookie-1"+username, null);
                cookieGetUserDetails.setMaxAge(300);

                // 1. fetch the password from the auth-db
                credential = credentialRepository.findById(username);
                log.info("CREDENTIAL FOR "+username+" FETCHED FROM THE AUTH_DB");
                // 2. set the password field in the userDetailView object declared above
                userDetailView.setUsername(username);
                userDetailView.setPassword(credential.get().getPassword());
                log.info("USERNAME AND PASSWORD SET IN THE USERDETAILVIEW OBJECT");
                // 3A send an ASYNC http request to the user-service to fetch the other user-detail data (email,phone,...) | SYNC [WAITING] HTTP REST API REQUEST
                Mono<UserDetail> user_service_response = webClientUserService.get().header("username", username).
                        retrieve().bodyToMono(UserDetail.class); // SEND THE ASYNC GET REQUEST
                log.info("ASYNC GET REQUEST SENT TO THE USER-SERVICE"); // FIRE & FORGET
                // 3B RESPONSE HANDLER TO BE WRITTEN HERE BUT WILL BE EXECUTED NOT NOW BUT WHEN THE RESPONSE GETS BACK
                user_service_response.subscribe(
                        (r) -> {
                            log.info(r+" from the user- service");
                            userDetailView.setEmail(r.email);
                            userDetailView.setPhone(r.phone);
                            userDetailView.setFirstName(r.firstName);
                            userDetailView.setLastName(r.lastName);
                            // SEND BACK THE COMPLETE RESPONSE OVER WEBSOCKET
                            // FRONT-END WILL POLL FOR THE EVENTUAL RESPONSE PERIODICALLY
                            // WE WILL STORE THE FINAL RESPONSE IN AN EXTERNAL CACHE LIKE REDIS
                            redisTemplate.opsForValue().set(cookieGetUserDetails.getName(),userDetailView);
                        },
                        error ->
                        {
                            log.info("error processing the response "+error.getMessage());
                            redisTemplate.opsForValue().
                                    set(String.valueOf(cookieGetUserDetails.getName()+cookieGetUserDetails.getValue()), error.getMessage());
                        });
                // 4. if a proper response is received, set the other user-detail data in the userDetailView object declared above
                // 5. return the userDetailView object as the response


                response.addCookie(cookieGetUserDetails);
                return ResponseEntity.ok("USER DETAILS ARE BEING FETCHED...[plz be patient]"); // AN INTERIM RESPONSE WITH A COOKIE

            }




        }
        else {

            log.info("found a relevant cookie.. initiating follow up logic");
            Cookie followup_cookie =  cookieList.stream().
                    filter(cookie -> cookie.getName().equals("auth-service-cookie-1"+username)).findAny().get();
            String followup_cookie_key = followup_cookie.getName();
            UserDetailView finalResponse = (UserDetailView) redisTemplate.opsForValue().get(followup_cookie_key);

            return ResponseEntity.ok(finalResponse);

        }

    }



}
