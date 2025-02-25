package com.secor.jdev25authservice;

import com.fasterxml.jackson.annotation.JsonAnySetter;
import lombok.Getter;
import lombok.Setter;

import java.io.Serializable;

@Getter
@Setter
public class UserDetailView implements Serializable {

    private String username;
    private String password;
    private String firstName;
    private String lastName;
    private String phone;
    private String email;

}
