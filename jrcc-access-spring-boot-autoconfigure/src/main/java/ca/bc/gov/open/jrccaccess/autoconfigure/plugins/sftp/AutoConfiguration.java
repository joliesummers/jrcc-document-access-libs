package ca.bc.gov.open.jrccaccess.autoconfigure.plugins.sftp;

import ca.bc.gov.open.jrccaccess.autoconfigure.services.DocumentReadyHandler;
import ca.bc.gov.open.jrccaccess.libs.services.exceptions.DocumentMessageException;
import com.jcraft.jsch.ChannelSftp;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.integration.annotation.InboundChannelAdapter;
import org.springframework.integration.annotation.Poller;
import org.springframework.integration.annotation.ServiceActivator;
import org.springframework.integration.core.MessageSource;
import org.springframework.integration.file.filters.AcceptOnceFileListFilter;
import org.springframework.integration.file.remote.session.CachingSessionFactory;
import org.springframework.integration.file.remote.session.SessionFactory;
import org.springframework.integration.sftp.filters.SftpSimplePatternFileListFilter;
import org.springframework.integration.sftp.inbound.SftpInboundFileSynchronizer;
import org.springframework.integration.sftp.inbound.SftpInboundFileSynchronizingMessageSource;
import org.springframework.integration.sftp.session.DefaultSftpSessionFactory;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessagingException;

import javax.websocket.MessageHandler;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;

@Configuration
@ComponentScan
@EnableConfigurationProperties(SftpInputProperties.class)
@ConditionalOnProperty(
        value="bcgov.access.input.plugin",
        havingValue = "sftp"
)
public class AutoConfiguration {

    private Logger logger = LoggerFactory.getLogger(AutoConfiguration.class);

    private SftpInputProperties properties;

    public AutoConfiguration(SftpInputProperties sftpInputProperties) {
        this.properties = sftpInputProperties;

        logger.debug("SFTP Configuration: Host => [{}]", this.properties.getHost());
        logger.debug("SFTP Configuration: Port => [{}]", this.properties.getPort());

    }


    @Bean
    public SessionFactory<ChannelSftp.LsEntry> sftpSessionFactory() {
        DefaultSftpSessionFactory factory = new DefaultSftpSessionFactory(true);
        factory.setHost(properties.getHost());
        factory.setPort(properties.getPort());
        factory.setUser(properties.getUsername());
        factory.setPassword(properties.getPassword());
        factory.setAllowUnknownKeys(true);
        return new CachingSessionFactory<ChannelSftp.LsEntry>(factory);
    }


    @Bean
    public SftpInboundFileSynchronizer sftpInboundFileSynchronizer() {
        SftpInboundFileSynchronizer fileSynchronizer = new SftpInboundFileSynchronizer(sftpSessionFactory());
        fileSynchronizer.setDeleteRemoteFiles(false);
        fileSynchronizer.setRemoteDirectory("/home/master/");
        fileSynchronizer.setFilter(new SftpSimplePatternFileListFilter("*.xml"));
        return fileSynchronizer;
    }

    @Bean
    @InboundChannelAdapter(channel = "sftpChannel", poller = @Poller(fixedDelay = "5000"))
    public MessageSource<File> sftpMessageSource() {
        SftpInboundFileSynchronizingMessageSource source =
                new SftpInboundFileSynchronizingMessageSource(sftpInboundFileSynchronizer());
        source.setLocalDirectory(new File("C:/Users/177226/Downloads/Justice"));
        source.setAutoCreateLocalDirectory(true);
        source.setLocalFilter(new AcceptOnceFileListFilter<File>());
        source.setMaxFetchSize(1);
        return source;
    }

    @Bean
    @ServiceActivator(inputChannel = "sftpChannel")
    public MessageHandler handler(DocumentReadyHandler documentReadyHandler) {
        return new MessageHandler() {

            public void handleMessage(Message<File> message) throws MessagingException, DocumentMessageException {

                try {
                    documentReadyHandler.handle(new String(Files.readAllBytes(Paths.get(message.getPayload().getPath()))), "test");
                    System.out.println();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }

        };
    }


}
