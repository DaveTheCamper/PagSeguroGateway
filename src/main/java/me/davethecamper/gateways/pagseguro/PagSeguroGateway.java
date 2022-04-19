package me.davethecamper.gateways.pagseguro;

import java.io.IOException;
import java.math.BigDecimal;
import java.util.UUID;

import org.bukkit.configuration.file.FileConfiguration;

import com.squareup.okhttp.Call;
import com.squareup.okhttp.OkHttpClient;
import com.squareup.okhttp.Request;
import com.squareup.okhttp.Response;

import br.com.uol.pagseguro.api.PagSeguro;
import br.com.uol.pagseguro.api.PagSeguroEnv;
import br.com.uol.pagseguro.api.checkout.CheckoutRegistration;
import br.com.uol.pagseguro.api.checkout.CheckoutRegistrationBuilder;
import br.com.uol.pagseguro.api.checkout.RegisteredCheckout;
import br.com.uol.pagseguro.api.common.domain.builder.PaymentItemBuilder;
import br.com.uol.pagseguro.api.common.domain.enums.Currency;
import br.com.uol.pagseguro.api.credential.Credential;
import me.davethecamper.cashshop.api.CashShopGateway;
import me.davethecamper.cashshop.api.info.InitializationResult;
import me.davethecamper.cashshop.api.info.PlayerInfo;
import me.davethecamper.cashshop.api.info.ProductInfo;
import me.davethecamper.cashshop.api.info.TransactionInfo;
import me.davethecamper.cashshop.api.info.TransactionResponse;
import me.davethecamper.cashshop.libs.json.JSONObject;

public class PagSeguroGateway implements CashShopGateway {
	
	private Credential credentials;
	private PagSeguro pagSeguro;
	
	private String token;
	private String email;

	public void generateConfigurationFile(FileConfiguration fc) {
		fc.set("email", "default@default.com");
		fc.set("token", "ABCD12345");
	}

	public TransactionInfo generateTransaction(ProductInfo product, PlayerInfo player) {
		UUID reference = UUID.randomUUID();
		System.out.println(product.getAmount());
		CheckoutRegistration cr = new CheckoutRegistrationBuilder()
				.withCurrency(Currency.BRL)
				.addItem(new PaymentItemBuilder()
							.withId("cash")
							.withAmount(new BigDecimal(product.getAmount()))
							.withDescription(product.getProductName())
							.withQuantity(1))
				.withReference(reference.toString())
				.build();
		
		RegisteredCheckout transaction = pagSeguro.checkouts().register(cr);
		
		return new TransactionInfo(transaction.getRedirectURL(), reference.toString());
	}

	public String getIdentifier() {
		return "PagSeguro";
	}
	
	public String getColoredDisplayName() {
		return "§6§lPag§a§lSeguro";
	}

	public InitializationResult init(FileConfiguration fc, String currency) {
		this.token = fc.getString("token");
		this.email = fc.getString("email");
		credentials = Credential.sellerCredential(this.email, this.token);
		PagSeguroEnv environment = PagSeguroEnv.PRODUCTION;
		pagSeguro = PagSeguro.instance(credentials, environment);
		
		try {
			generateTransaction(new ProductInfo(1, "Testing", "BRL"), new PlayerInfo(null, null, null));
		} catch (Exception e) {
			e.printStackTrace();
			return InitializationResult.INVALID_CREDENTIALS;
		}
		
		
		return InitializationResult.INITIALIZATED;
	}

	public boolean isValidCurrency(String arg0) {
		return arg0.equalsIgnoreCase("brl");
	}

	public TransactionResponse verifyTransaction(String arg0) {
		
		//TransactionDetail transaction = pagSeguro.transactions().search().byCode(arg0);
		
		OkHttpClient client = new OkHttpClient();
		Request request = new Request.Builder()
		  .url("https://ws.pagseguro.uol.com.br/v2/transactions/?email=" + email + "&token=" + token + "&reference=" + arg0)
		  .method("GET", null)
		  .addHeader("Content-Type", "application/json")
		  .addHeader("x-api-version", "4.0")
		  .build();

	    Call call = client.newCall(request);
	    Response response;
		try {
			response = call.execute();
			JSONObject jsonObject = me.davethecamper.cashshop.libs.json.XML.toJSONObject(response.body().string());
			int status = getStatus(jsonObject, "transactionSearchResult.transactions.transaction.status");
			switch (status) {
				case 1:
				case 2:
					return TransactionResponse.WAITING_FOR_PAYMENT;
					
				case 3:
				case 4:
					return TransactionResponse.APPROVED;
					
				case 5:
				case 6:
					return TransactionResponse.REFUNDED;
					
				case 7:
					return TransactionResponse.CANCELLED;
					
			}
		} catch (IOException e) {
			e.printStackTrace();
			try {
				System.out.println(call.execute().body().string());
			} catch (IOException e1) {
				// TODO Auto-generated catch block
				e1.printStackTrace();
			}
			return TransactionResponse.WAITING_FOR_PAYMENT;
		}
		
		
		return null;
	}

	private static int getStatus(JSONObject json, String path) {
		int status = -1;
		String partes[] = path.replace('.', ';').split(";");
		
		for (int i = 0; i < partes.length-1; i++) {	
			json = json.has(partes[i]) && json.get(partes[i]) != null ? json.getJSONObject(partes[i]) : json;
		}
		
		status = json.has(partes[partes.length-1]) ? json.getInt(partes[partes.length-1]) : -1;
		
		return status;
	}
}
