package satc.workshop.pedido;

public record Pedido(String id, String cliente, String produto, int quantidade, String status) {
    public record Entrada(String cliente, String produto, Integer quantidade) {}
}
