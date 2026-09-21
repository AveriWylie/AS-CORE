Six classes in the config package, and only one of them is worth reading regularly.

TRIVIAL, ONE ANNOTATION AND AN EMPTY BODY

    SchedulingConfig    @EnableScheduling, without which every @Scheduled method is silently inert
    EgressConfig        @EnableConfigurationProperties(OpenCloudProperties.class), the binding for
                        shayveri.opencloud

Nothing is constructed by hand, so there is no body. They exist because the annotation has to live
somewhere, and a class of its own is where the reason for it can be written down.

ONE BEAN EACH

    AsyncConfig     the virtual-thread executor, injected wherever work is handed off the request thread
    AsdbConfig      the shared AsdbBinaryClient, only when shayveri.store=asdb

THE MIDDLE CASE

    WebSocketConfig     three methods, two of them real decisions: where clients connect (/ws, and which
                        origins), and what destinations exist (/topic). The third registers
                        StompAuthInterceptor on the inbound channel, which is what makes the STOMP side
                        authenticated at all.

THE ONE WITH A POLICY IN IT

    SecurityConfig      the filter chain and the role table. See security.md.
