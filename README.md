# Mapper
mybatis common mapper

# How to Use Common Mapper:
1. extends BaseMapper
2. call baseMapper's common function
3. enjoy~

# How to Use @CreatedBy / ModifiedBy

1. Define your entity, emaple:
```java
@Data
public class UserOrgScope implements java.io.Serializable{	
 
	//columns START
	private Long id;
	 
	private Long ucId;
	 
	private String orgCode;
	
	private Integer status;
    @CreatedBy
	private Long creator;
	 
	private java.util.Date createTime;
	
    @ModifiedBy
	private Long updator;
	
	private java.util.Date updateTime;
```

2. define your own entity interceptor, example:

```java
       final EntityInterceptor entityInterceptor = new EntityInterceptor();
               
       entityInterceptor.setAuditorAware(() -> {
             final String header = ((ServletRequestAttributes) RequestContextHolder.getRequestAttributes()).getRequest().getHeader(XHeaders.LOGIN_USER_ID);
             return Long.valueOf(header);
       });
```
3. modify your mybatis configuration,(depends on what framework you are using), example (using spring-boot-mybatis-starter):

```java
    @Configuration
    static class DecryptInterceptorConfig {

        @Bean
        public Interceptor[] configurationCustomizer(CipherSpi cipherSpi) {
            final EntityInterceptor entityInterceptor = new EntityInterceptor();

            entityInterceptor.setAuditorAware(() -> {
                final String header = ((ServletRequestAttributes) RequestContextHolder.getRequestAttributes()).getRequest().getHeader(XHeaders.LOGIN_USER_ID);
                return Long.valueOf(header);
            });
            return new Interceptor[]{new DecryptInterceptor(cipherSpi), entityInterceptor};
        }
    }   
```

4. Enjoy 