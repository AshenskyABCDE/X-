



## 登录相关：

该项目通过jwt进行登录校验，所以token的解析的行为放到了网关中，由网关把用户信息放入请求头，传递给下游微服务。



通过gateway 网关

```java
    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        // 1.获取请求request信息
        ServerHttpRequest request = exchange.getRequest();
        String method = request.getMethodValue();
        String path = request.getPath().toString();
        String antPath = method + ":" + path;

        // 2.判断是否是无需登录的路径
        if(isExcludePath(antPath)){
            // 直接放行
            return chain.filter(exchange);
        }

        // 3.尝试获取用户信息
        List<String> authHeaders = exchange.getRequest().getHeaders().get(AUTHORIZATION_HEADER);
        String token = authHeaders == null ? "" : authHeaders.get(0);
        R<LoginUserDTO> r = authUtil.parseToken(token);

        // 4.如果用户是登录状态，尝试更新请求头，传递用户信息
        if(r.success()){
            exchange.mutate()
                    .request(builder -> builder.header(USER_HEADER, r.getData().getUserId().toString()))
                    .build();
        }

        // 5.校验权限
        authUtil.checkAuth(antPath, r);

        // 6.放行
        return chain.filter(exchange);
    }
```



之后到拦截器中，在controller层之前将id存储到UserContext中

```java
@Slf4j
public class UserInfoInterceptor implements HandlerInterceptor {

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        // 1.尝试获取头信息中的用户信息
        String authorization = request.getHeader(JwtConstants.USER_HEADER);
        // 2.判断是否为空
        if (authorization == null) {
            return true;
        }
        // 3.转为用户id并保存到UserContext中
        try {
            Long userId = Long.valueOf(authorization);
            UserContext.setUser(userId);
            return true;
        } catch (NumberFormatException e) {
            log.error("用户身份信息格式不正确，{}, 原因：{}", authorization, e.getMessage());
            return true;
        }
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) throws Exception {
        // 清理用户信息
        UserContext.removeUser();
    }
}
```

至此，用户的信息存入请求头的即使如此，可直接调用UserContext.getId，同时这是基于ThreadLocal，所以可以避开不同请求，避免线程安全问题



## 细节：

spring @validated （参数是如何检验的，可以减少if分支的部分）

@NotNULL 检验非空

@Max 检验数据是否在最大范围之内