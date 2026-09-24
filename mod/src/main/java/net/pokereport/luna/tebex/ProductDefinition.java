package net.pokereport.luna.tebex;

import net.pokereport.luna.ui.Tablist;

/**
 * Especificación inmutable de un producto comercial de PokeReport Network.
 * La fuente de verdad del producto vive aquí y en el PackageRegistry,
 * nunca en los parámetros externos recibidos por comandos de Tebex.
 */
public record ProductDefinition(
    String packageId,
    ProductType type,
    long amount,
    Tablist.Rank rank,
    Tablist.Rank requiredPreviousRank
) {
    public ProductDefinition {
        if (packageId == null || packageId.isBlank()) {
            throw new IllegalArgumentException("packageId no puede ser nulo ni vacío");
        }
        if (type == null) {
            throw new IllegalArgumentException("ProductType no puede ser nulo");
        }
        if (type == ProductType.LUNACOINS && amount <= 0) {
            throw new IllegalArgumentException("El monto de LunaCoins debe ser mayor que 0: " + amount);
        }
        if (type == ProductType.RANK && (rank == null || rank.equipo)) {
            throw new IllegalArgumentException("Rango inválido o restringido a equipo: " + rank);
        }
        if (type == ProductType.RANK_UPGRADE) {
            if (rank == null || rank.equipo || requiredPreviousRank == null || requiredPreviousRank.equipo) {
                throw new IllegalArgumentException("Upgrade de rango inválido o contiene rangos de staff");
            }
            if (rank == requiredPreviousRank) {
                throw new IllegalArgumentException("El rango destino no puede ser igual al previo: " + rank);
            }
            if (rank.escalon <= requiredPreviousRank.escalon) {
                throw new IllegalArgumentException("El escalón de destino (" + rank.escalon + 
                        ") debe ser estrictamente superior al previo (" + requiredPreviousRank.escalon + ")");
            }
        }
    }
}
