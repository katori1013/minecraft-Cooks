package jp.minecraftcooks.core.recipe;

/** recipes.yml が読めないときの例外。メッセージに場所と識別子を入れる。 */
public class RecipeException extends RuntimeException {
    public RecipeException(String message) {
        super(message);
    }
}
