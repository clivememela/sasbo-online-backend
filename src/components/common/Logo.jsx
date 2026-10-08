import logo from "../../assets/images/logo.png";

export default function Logo({ className = "h-12 w-auto", alt = "SASBO Logo" }) {
  return (
    <img src={logo} alt={alt} className={className} />
  );
}
