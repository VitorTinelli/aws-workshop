package satc.workshop.pedido;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/pedidos")
@RequiredArgsConstructor
public class PedidoController {
    private final PedidoService service;

    @PostMapping
    @ResponseStatus(HttpStatus.ACCEPTED)
    public Pedido criar(@RequestBody Pedido.Entrada entrada) { return service.criar(entrada); }

    @GetMapping("/{id}")
    public Pedido buscar(@PathVariable String id) { return service.buscar(id); }

    @GetMapping("/{id}/comprovante")
    public ResponseEntity<byte[]> comprovante(@PathVariable String id) {
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_TYPE, "text/plain; charset=utf-8")
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"comprovante.txt\"")
                .body(service.comprovante(id));
    }
}
