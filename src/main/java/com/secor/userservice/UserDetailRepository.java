package com.secor.userservice;

import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;

public interface UserDetailRepository extends MongoRepository<UserDetail, String> {
}
