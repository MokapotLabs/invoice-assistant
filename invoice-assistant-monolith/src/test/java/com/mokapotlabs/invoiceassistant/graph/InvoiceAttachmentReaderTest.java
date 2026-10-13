package com.mokapotlabs.invoiceassistant.graph;
import static org.junit.jupiter.api.Assertions.*;
import com.mokapotlabs.invoiceassistant.app.InvoiceAssistantConfiguration;
import com.mokapotlabs.invoiceassistant.domain.InvoiceDocument;
import com.mokapotlabs.invoiceassistant.web.InvoicePayloadAuthorizer;
import java.io.ByteArrayInputStream;
import java.nio.file.*;
import java.time.Instant;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.pipelineframework.config.boundary.*;
import org.pipelineframework.connector.*;
import org.pipelineframework.connector.objectingest.FilesystemObjectConnector;
class InvoiceAttachmentReaderTest {
 @TempDir Path root;
 @Test void verifiesOwnedBytesAndRejectsTamperingAndMismatchedMetadata() throws Exception {
  var bindings=ConnectorBindingRegistry.fromProviders(List.of(new ConnectorBindingDefinition(ConnectorBindingName.of("invoice-documents"),new FilesystemObjectConnector().id(),1,ConnectorConfigurationDocument.empty())),List.of(new FilesystemObjectConnector()));
  var runtime=ConnectorRuntimeContext.empty();var authorizer=new InvoicePayloadAuthorizer();var transfer=new OwnedPayloadTransfer(bindings,runtime,authorizer);
  var boundary=new PipelineHttpPayloadBoundaryConfig("invoice-file",PipelineHttpPayloadBoundaryConfig.Direction.UPLOAD,"invoice-document-store","UploadedInvoice","invoice",List.of("application/pdf"),2900000,"invoice.write");
  var target=new PipelineObjectPublishConfig("invoice-document-store","object","filesystem",Optional.of("invoice-documents"),Map.of("root",root.toString()),PipelineObjectNamingConfig.defaults(),PipelineObjectPublishPayloadConfig.defaults(),PipelineObjectPublishGroupingConfig.defaults());
  byte[] bytes="%PDF-1.7\nsynthetic fixture\n".getBytes(java.nio.charset.StandardCharsets.UTF_8);
  var reference=transfer.upload(boundary,target,System.getProperty("user.name"),"local","invoices","application/pdf",new ByteArrayInputStream(bytes));
  var document=new InvoiceDocument(UUID.randomUUID(),"fixture.pdf","application/pdf",reference.sizeBytes(),reference.checksum(),Instant.now(),reference);
  var reader=new InvoiceAttachmentReader(new InvoiceAssistantConfiguration(root,2900000),bindings,runtime,authorizer);
  assertArrayEquals(bytes,reader.read(document));
  var mismatched=new InvoiceDocument(document.documentId(),"fixture.pdf","application/pdf",reference.sizeBytes()+1,reference.checksum(),Instant.now(),reference);
  assertThrows(IllegalArgumentException.class,()->reader.read(mismatched));
  bytes[bytes.length-2]='x';Files.write(root.resolve(reference.key()),bytes);
  assertThrows(Exception.class,()->reader.read(document));
 }
}
