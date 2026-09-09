FROM amazoncorretto:17

#ENV ENV_PROFILE=dev

#ENV TZ=America/New_York

ADD ./target/azureopenaiapi-1.0.0.jar /var/lib/azureopenaiapiChatbot.jar
#ADD ./target/BrightWaveSolutions.pdf /var/lib/

EXPOSE 8080

ENTRYPOINT java -Dservice-name=azureopenaiapi-Chatbot  -jar /var/lib/azureopenaiapiChatbot.jar