package kr.hhplus.be.server.product;

import kr.hhplus.be.server.application.product.ProductService;
import kr.hhplus.be.server.infrastructure.product.persistence.ProductEntity;
import kr.hhplus.be.server.presentation.product.ProductController;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(ProductController.class)
class ProductControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ProductService productService;

    @Test
    void shouldGetProduct() throws Exception {
        // given
        UUID productId = UUID.randomUUID();
        ProductEntity product = new ProductEntity("Product A", "Description", BigDecimal.valueOf(10000), 100);
        when(productService.getProduct(any(UUID.class))).thenReturn(product);

        // when & then
        mockMvc.perform(get("/api/products/{id}", productId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").exists())
                .andExpect(jsonPath("$.name").value("Product A"))
                .andExpect(jsonPath("$.description").value("Description"))
                .andExpect(jsonPath("$.price").value(10000))
                .andExpect(jsonPath("$.stock").value(100))
                .andExpect(jsonPath("$.status").value("SELLING"));
    }

    @Test
    void shouldGetAllProducts() throws Exception {
        // given
        List<ProductEntity> products = List.of(
                new ProductEntity("Product A", "Desc A", BigDecimal.valueOf(10000), 100),
                new ProductEntity("Product B", "Desc B", BigDecimal.valueOf(20000), 50)
        );
        when(productService.getProducts()).thenReturn(products);

        // when & then
        mockMvc.perform(get("/api/products"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$[0].name").value("Product A"))
                .andExpect(jsonPath("$[1].name").value("Product B"));
    }

    @Test
    void shouldCreateProduct() throws Exception {
        // given
        ProductEntity product = new ProductEntity("New Product", "New Description", BigDecimal.valueOf(15000), 50);
        when(productService.createProduct(anyString(), anyString(), any(BigDecimal.class), anyInt())).thenReturn(product);

        String requestBody = """
                {
                    "name": "New Product",
                    "description": "New Description",
                    "price": 15000,
                    "stock": 50
                }
                """;

        // when & then
        mockMvc.perform(post("/api/products")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name").value("New Product"))
                .andExpect(jsonPath("$.price").value(15000))
                .andExpect(jsonPath("$.stock").value(50))
                .andExpect(jsonPath("$.status").value("SELLING"));
    }
}
