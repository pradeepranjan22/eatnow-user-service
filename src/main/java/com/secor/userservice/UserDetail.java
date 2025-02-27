package com.secor.userservice;

import lombok.Getter;
import lombok.Setter;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

@Document(collection = "userdetails")
@Getter @Setter
public class UserDetail {

    @Id
    private String username;
    private String fullname;
    private String usertype;

}
