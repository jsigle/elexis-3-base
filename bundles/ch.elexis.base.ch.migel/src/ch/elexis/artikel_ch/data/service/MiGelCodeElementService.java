package ch.elexis.artikel_ch.data.service;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;
import org.slf4j.LoggerFactory;

import ch.elexis.core.model.IArticle;
import ch.elexis.core.model.ICodeElement;
import ch.elexis.core.services.ICodeElementService.CodeElementTyp;
import ch.elexis.core.services.ICodeElementServiceContribution;
import ch.elexis.core.services.IModelService;
import ch.elexis.core.services.INamedQuery;
import ch.elexis.core.types.ArticleTyp;

@Component
public class MiGelCodeElementService implements ICodeElementServiceContribution {
	
	public static final String MIGEL_NAME = "MiGeL";
	
	@Reference(target = "(" + IModelService.SERVICEMODELNAME + "=ch.elexis.core.model)")
	private IModelService coreModelService;
	
	@Override
	public String getSystem(){
		return MIGEL_NAME;
	}
	
	@Override
	public CodeElementTyp getTyp(){
		return CodeElementTyp.ARTICLE;
	}
	
	@Override
	public Optional<ICodeElement> loadFromCode(String code, Map<Object, Object> context){
		INamedQuery<IArticle> query = coreModelService.getNamedQuery(IArticle.class, "typ", "code");
		
		List<IArticle> found = query
			.executeWithParameters(query.getParameterMap("typ", ArticleTyp.MIGEL, "code", code));
		if (!found.isEmpty()) {
			if (found.size() > 1) {
				LoggerFactory.getLogger(getClass())
					.warn("Found more than one " + ArticleTyp.MIGEL.getCodeSystemName()
						+ " with code [" + code + "] using first");
			}
			return Optional.of(found.get(0));
		} else {
			query = coreModelService.getNamedQuery(IArticle.class, "typ", "id");
			found = query
				.executeWithParameters(query.getParameterMap("typ", ArticleTyp.MIGEL, "id", code));
			if (!found.isEmpty()) {
				if (found.size() > 1) {
					LoggerFactory.getLogger(getClass())
						.warn("Found more than one " + ArticleTyp.MIGEL.getCodeSystemName()
							+ " with id [" + code + "] using first");
				}
				return Optional.of(found.get(0));
			}
		}
		return Optional.empty();
	}
	
	@SuppressWarnings("unchecked")
	@Override
	public List<ICodeElement> getElements(Map<Object, Object> context){
		INamedQuery<IArticle> query = coreModelService.getNamedQuery(IArticle.class, "typ");
		return (List<ICodeElement>) (List<?>) query
			.executeWithParameters(query.getParameterMap("typ", ArticleTyp.MIGEL));
	}
}
